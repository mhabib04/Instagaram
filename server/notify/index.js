/**
 * Instagaram push notifications (AWS Lambda, Node.js 24, Function URL).
 *
 * The app calls this after it has written a chat message, a like or a follow:
 *   POST { "type": "chat" | "like" | "follow", "id": "<Firestore document id>" }
 *
 * Nothing in the request is trusted: the function reads the document itself, works out the
 * recipient, and sends an FCM data message to that user's fcmToken. Each document is notified
 * at most once (notifySent/{type}_{id}), and only while it is fresh, so the public URL cannot
 * be used to spam arbitrary text or replay old events.
 *
 * Environment variable:
 *   FIREBASE_SERVICE_ACCOUNT = the full JSON of the Firebase service-account key
 */
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)),
  });
}
const db = admin.firestore();

// Field and collection names used by the Android app (utilities/Constants.java).
const USERS = "users", CHATS = "chats", LIKES = "likes", POSTS = "posts", FOLLOWS = "follows", SENT = "notifySent";
const MAX_AGE_MS = 10 * 60 * 1000;

const reply = (statusCode, body) => ({
  statusCode,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

exports.handler = async (event) => {
  let request;
  try {
    const raw = event.isBase64Encoded ? Buffer.from(event.body || "", "base64").toString() : event.body;
    request = JSON.parse(raw || "{}");
  } catch {
    return reply(400, { error: "invalid JSON" });
  }
  const { type, id } = request;
  if (!Object.hasOwn(BUILDERS, type) || typeof id !== "string" || !/^[A-Za-z0-9_-]{1,200}$/.test(id)) {
    return reply(400, { error: "expected {type: chat|like|follow, id}" });
  }

  try {
    const notification = await BUILDERS[type](id);
    if (!notification) return reply(200, { sent: false, reason: "nothing to notify" });

    // Claim this document; create() fails if another call already notified it.
    try {
      await db.collection(SENT).doc(`${type}_${id}`).create({ at: admin.firestore.FieldValue.serverTimestamp() });
    } catch (e) {
      if (e.code === 6 /* ALREADY_EXISTS */) return reply(200, { sent: false, reason: "already notified" });
      throw e;
    }

    await send(notification.recipientId, notification.data);
    return reply(200, { sent: true });
  } catch (e) {
    console.error("notify failed", type, id, e);
    return reply(500, { error: "internal error" });
  }
};

function isFresh(timestamp) {
  return timestamp && Date.now() - timestamp.toDate().getTime() <= MAX_AGE_MS;
}

/** "senderUsername: message" for the receiver of a chat message. */
async function chatNotification(id) {
  const chat = await db.collection(CHATS).doc(id).get();
  if (!chat.exists || !isFresh(chat.get("timestamp"))) return null;
  const senderId = chat.get("senderId"), receiverId = chat.get("receiverId");
  if (!senderId || !receiverId || senderId === receiverId) return null;
  const sender = await db.collection(USERS).doc(senderId).get();
  if (!sender.exists) return null;
  return {
    recipientId: receiverId,
    data: {
      type: "chat",
      senderId,
      senderUsername: sender.get("username") || "",
      senderImage: sender.get("image") || "",
      message: String(chat.get("message") || "").slice(0, 500),
    },
  };
}

/** "likerUsername liked your post" for the post's owner (never for liking your own post). */
async function likeNotification(id) {
  const like = await db.collection(LIKES).doc(id).get();
  if (!like.exists || !isFresh(like.get("timestamp"))) return null;
  const likerId = like.get("userId"), postId = like.get("postId");
  if (!likerId || !postId) return null;
  const post = await db.collection(POSTS).doc(postId).get();
  if (!post.exists) return null;
  const ownerId = post.get("userId");
  if (!ownerId || ownerId === likerId) return null;
  const liker = await db.collection(USERS).doc(likerId).get();
  if (!liker.exists) return null;
  return {
    recipientId: ownerId,
    data: {
      type: "like",
      postId,
      likerId,
      likerUsername: liker.get("username") || "",
      postImage: post.get("imagePosts") || "",
    },
  };
}

/** "followerUsername started following you" for the followed user. */
async function followNotification(id) {
  const follow = await db.collection(FOLLOWS).doc(id).get();
  if (!follow.exists || !isFresh(follow.get("timestamp"))) return null;
  const followerId = follow.get("followerId"), followingId = follow.get("followingId");
  if (!followerId || !followingId || followerId === followingId) return null;
  const follower = await db.collection(USERS).doc(followerId).get();
  if (!follower.exists) return null;
  return {
    recipientId: followingId,
    data: {
      type: "follow",
      followerId,
      followerUsername: follower.get("username") || "",
      followerImage: follower.get("image") || "",
    },
  };
}

// Builds the notification for each request type; any other type is rejected.
const BUILDERS = { chat: chatNotification, like: likeNotification, follow: followNotification };

/** Data-only, high priority: the app builds the (localised) notification itself. */
async function send(recipientId, data) {
  const recipient = await db.collection(USERS).doc(recipientId).get();
  const token = recipient.exists ? recipient.get("fcmToken") : null;
  if (!token) return;
  try {
    await admin.messaging().send({ token, data, android: { priority: "high" } });
  } catch (e) {
    // Uninstalled app or rotated token: forget it so we stop trying.
    if (e.code === "messaging/registration-token-not-registered" || e.code === "messaging/invalid-registration-token") {
      await recipient.ref.update({ fcmToken: admin.firestore.FieldValue.delete() });
      return;
    }
    throw e;
  }
}
