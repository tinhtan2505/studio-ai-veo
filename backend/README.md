# base-java-spring-be

## Veo video generation

The Studio UI calls the backend proxy at `POST /api/veo/videos` using multipart form data. The backend starts the long-running Veo operation, exposes `GET /api/veo/videos/{id}` for polling, and proxies the finished MP4 from `GET /api/veo/videos/{id}/download`.

Set the Gemini API key in the process environment; do not put it in source files:

```powershell
$env:GEMINI_API_KEY = "your-gemini-api-key"
$env:APP_VEO_REQUIRE_AUTH = "false"
.\gradlew.bat bootRun
```

The local Studio UI defaults to `http://localhost:6661`. Set `APP_VEO_REQUIRE_AUTH=true` before exposing the backend publicly.
