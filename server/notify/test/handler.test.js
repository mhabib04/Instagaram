// Offline tests for index.js with an in-memory Firestore/FCM stand-in.  Run: node --test test/
const test = require("node:test");
const assert = require("node:assert");
const Module = require("node:module");

// ---- tiny fake of the parts of firebase-admin that index.js uses ----
const store = new Map();          // "collection/id" -> data
const sent = [];                  // messages passed to messaging().send
let sendError = null;
const ts = (ms) => ({ toDate: () => new Date(ms) });
const docRef = (col, id) => ({
  async get() {
    const data = store.get(`${col}/${id}`);
    return { exists: !!data, get: (k) => data && data[k], ref: docRef(col, id) };
  },
  async create(data) {
    if (store.has(`${col}/${id}`)) { const e = new Error("exists"); e.code = 6; throw e; }
    store.set(`${col}/${id}`, data);
  },
  async update(patch) {
    const data = { ...store.get(`${col}/${id}`) };
    for (const [k, v] of Object.entries(patch)) { if (v === "DELETE") delete data[k]; else data[k] = v; }
    store.set(`${col}/${id}`, data);
  },
});
const fakeAdmin = {
  apps: [],
  initializeApp() { this.apps.push({}); },
  credential: { cert: () => ({}) },
  firestore: Object.assign(() => ({ collection: (c) => ({ doc: (id) => docRef(c, id) }) }),
    { FieldValue: { serverTimestamp: () => "NOW", delete: () => "DELETE" } }),
  messaging: () => ({ async send(m) { if (sendError) throw sendError; sent.push(m); } }),
};
const realLoad = Module._load;
Module._load = (request, ...rest) => (request === "firebase-admin" ? fakeAdmin : realLoad(request, ...rest));
process.env.FIREBASE_SERVICE_ACCOUNT = "{}";
const { handler } = require("../index.js");

const call = async (body) => {
  const res = await handler({ body: JSON.stringify(body) });
  return { status: res.statusCode, ...JSON.parse(res.body) };
};
const reset = () => {
  store.clear(); sent.length = 0; sendError = null;
  store.set("users/alice", { username: "alice", image: "a.jpg", fcmToken: "tokA" });
  store.set("users/bob", { username: "bob", image: "b.jpg", fcmToken: "tokB" });
  store.set("posts/p1", { userId: "alice", imagePosts: "p1.jpg" });
};

test("rejects malformed requests", async () => {
  reset();
  assert.equal((await call({ type: "spam", id: "x" })).status, 400);
  assert.equal((await call({ type: "constructor", id: "x" })).status, 400);
  assert.equal((await call({ type: "chat", id: "../users/alice" })).status, 400);
  assert.equal((await handler({ body: "not json" })).statusCode, 400);
});

test("chat: notifies the receiver once, with data from Firestore", async () => {
  reset();
  store.set("chats/c1", { senderId: "bob", receiverId: "alice", message: "halo", timestamp: ts(Date.now()) });
  assert.deepEqual(await call({ type: "chat", id: "c1" }), { status: 200, sent: true });
  assert.equal(sent.length, 1);
  assert.equal(sent[0].token, "tokA");
  assert.deepEqual(sent[0].data, { type: "chat", senderId: "bob", senderUsername: "bob", senderImage: "b.jpg", message: "halo" });
  // second call for the same document is ignored
  assert.equal((await call({ type: "chat", id: "c1" })).reason, "already notified");
  assert.equal(sent.length, 1);
});

test("stale or missing documents are ignored", async () => {
  reset();
  store.set("chats/old", { senderId: "bob", receiverId: "alice", message: "x", timestamp: ts(Date.now() - 11 * 60 * 1000) });
  assert.equal((await call({ type: "chat", id: "old" })).sent, false);
  assert.equal((await call({ type: "chat", id: "nope" })).sent, false);
  assert.equal(sent.length, 0);
});

test("like: notifies the post owner, but not for liking your own post", async () => {
  reset();
  store.set("likes/p1_bob", { postId: "p1", userId: "bob", timestamp: ts(Date.now()) });
  assert.equal((await call({ type: "like", id: "p1_bob" })).sent, true);
  assert.equal(sent[0].token, "tokA");
  assert.equal(sent[0].data.likerUsername, "bob");
  assert.equal(sent[0].data.postId, "p1");

  store.set("likes/p1_alice", { postId: "p1", userId: "alice", timestamp: ts(Date.now()) });
  assert.equal((await call({ type: "like", id: "p1_alice" })).sent, false);
  assert.equal(sent.length, 1);
});

test("follow: notifies the followed user once, never for following yourself", async () => {
  reset();
  store.set("follows/bob_alice", { followerId: "bob", followingId: "alice", timestamp: ts(Date.now()) });
  assert.deepEqual(await call({ type: "follow", id: "bob_alice" }), { status: 200, sent: true });
  assert.equal(sent[0].token, "tokA");
  assert.deepEqual(sent[0].data, { type: "follow", followerId: "bob", followerUsername: "bob", followerImage: "b.jpg" });
  // unfollow + follow again rewrites the same document: no second push
  assert.equal((await call({ type: "follow", id: "bob_alice" })).reason, "already notified");

  store.set("follows/alice_alice", { followerId: "alice", followingId: "alice", timestamp: ts(Date.now()) });
  assert.equal((await call({ type: "follow", id: "alice_alice" })).sent, false);
  assert.equal(sent.length, 1);
});

test("an unregistered token is removed from the user", async () => {
  reset();
  store.set("chats/c2", { senderId: "bob", receiverId: "alice", message: "hi", timestamp: ts(Date.now()) });
  sendError = Object.assign(new Error("gone"), { code: "messaging/registration-token-not-registered" });
  assert.equal((await call({ type: "chat", id: "c2" })).status, 200);
  assert.equal(store.get("users/alice").fcmToken, undefined);
});
