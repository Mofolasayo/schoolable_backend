# Ops

Backend logs on the droplet:

```
CONTAINER=$(docker ps --filter label=service=schoolable-backend --format '{{.Names}}' | head -1)
docker logs --tail=200 -f "$CONTAINER"
```

Kamal proxy logs (TLS + routing):

```
docker logs --tail=200 -f kamal-proxy
```

One-off health check:

```
curl -i https://165-227-1-93.sslip.io/up
```

FCM (push notifications):

- Upload the Firebase service account JSON to the droplet (e.g. `/home/deploy/.config/schoolable/firebase-service-account.json`).
- Add `FCM_SERVICE_ACCOUNT_PATH` to `.kamal/secrets` with the full path.
- iOS devices also require an APNs auth key uploaded in Firebase Console → Cloud Messaging.
