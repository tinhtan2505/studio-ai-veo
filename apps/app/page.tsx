import { connection } from "next/server";
import { Suspense } from "react";
import StudioWorkspace from "./studio-workspace";

async function DynamicStudioWorkspace() {
  await connection();
  return <StudioWorkspace />;
}

function StudioFallback() {
  return (
    <main className="grid h-dvh place-items-center bg-[#070b14] text-slate-100">
      <div className="flex flex-col items-center gap-4 text-center">
        <div className="grid size-14 animate-pulse place-items-center rounded-2xl border border-indigo-400/20 bg-indigo-400/10 text-xl font-bold text-indigo-300">
          AI
        </div>
        <div>
          <p className="text-sm font-semibold">Đang khởi tạo Studio AI</p>
          <p className="mt-1 text-xs text-slate-500">Chuẩn bị không gian sản xuất…</p>
        </div>
      </div>
    </main>
  );
}

export default function Home() {
  return (
    <Suspense fallback={<StudioFallback />}>
      <DynamicStudioWorkspace />
    </Suspense>
  );
}
