/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    const backendUrl = process.env.STUDIO_BACKEND_URL ?? "http://localhost:6661";

    return [
      {
        source: "/api/omni/:path*",
        destination: `${backendUrl}/api/omni/:path*`,
      },
    ];
  },
  experimental: {
    agentFeedback: true,
    cpus: 1,
  },
  cacheComponents: true,
  partialPrefetching: true,
};

export default nextConfig;
