import FastestLapCard from "./components/FastestLapCard";

export default function Page() {
  return (
    <main className="min-h-screen flex items-center justify-center p-6 bg-gradient-to-b from-neutral-950 to-neutral-900 text-neutral-100">
      <div className="w-full max-w-2xl">
        <header className="mb-8">
          <p className="uppercase tracking-widest text-xs text-emerald-400 mb-2">Garage 61</p>
          <h1 className="text-3xl sm:text-4xl font-bold">Schnellste Runde · Spa-Francorchamps</h1>
          <p className="text-neutral-400 mt-2 text-sm">
            Liest deine persönliche Bestzeit in Spa direkt aus der Garage 61 API.
          </p>
        </header>
        <FastestLapCard />
      </div>
    </main>
  );
}
