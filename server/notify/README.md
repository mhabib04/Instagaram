# instagaram-notify (AWS Lambda)

Sends chat, like and follow push notifications through Firebase Cloud Messaging.
The Android app calls the Function URL with `{"type": "chat" | "like" | "follow", "id": "<document id>"}`;
the function reads that document from Firestore itself and notifies the recipient once.

## Build

```bash
npm install --omit=dev
npm test
```

Then zip `index.js`, `package.json` and `node_modules/` into `notify-lambda.zip`
(the `test/` folder is not needed).

## Deploy (AWS Console)

1. Region (top right): Asia Pacific (Jakarta) `ap-southeast-3`, or Singapore if Lambda is unavailable.
2. Lambda → **Create function** → Author from scratch → name `instagaram-notify`,
   runtime **Node.js 24.x** → Create.
3. **Code** → Upload from → .zip file → `notify-lambda.zip` → Save.
   Runtime settings → Handler must be `index.handler`.
4. **Configuration → General configuration** → Edit → Memory **256 MB**, Timeout **15 s**.
5. **Configuration → Environment variables** → Add
   `FIREBASE_SERVICE_ACCOUNT` = the whole content of the Firebase service-account key `.json`.
   Never commit that file.
6. **Configuration → Function URL** → Create → Auth type **NONE** → copy the URL.

## Smoke test (sends nothing)

```bash
curl -s -X POST "<FUNCTION_URL>" -H "Content-Type: application/json" -d '{"type":"chat","id":"does-not-exist"}'
```

Expected: `{"sent":false,"reason":"nothing to notify"}`. A 500 means the key / environment variable is wrong
(see the function's CloudWatch logs).
