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
