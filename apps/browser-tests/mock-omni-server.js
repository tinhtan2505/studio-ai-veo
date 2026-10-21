const http = require("node:http");

const server = http.createServer((request, response) => {
  if (request.url === "/health") {
    response.writeHead(200, { "content-type": "application/json" });
    response.end('{"status":"ok"}');
    return;
  }

  if (request.method === "GET" && /^\/api\/omni\/outputs\//.test(request.url || "")) {
    response.writeHead(200, {
      "content-type": "video/mp4",
      "content-disposition": 'attachment; filename="omni-output.mp4"',
      "content-length": "8",
    });
    response.end("mock-mp4");
    return;
  }

  response.writeHead(404, { "content-type": "application/json" });
  response.end('{"code":"MOCK_NOT_FOUND"}');
});

server.listen(6661, "127.0.0.1");

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
