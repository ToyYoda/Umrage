"use client";

import dynamic from "next/dynamic";
import SurveyForm from "./components/SurveyForm";
import ResultsChart from "./components/ResultsChart";

export default function Home() {
  return (
    <div className="flex min-h-screen items-start justify-center bg-zinc-50 font-sans dark:bg-black py-12">
      <main className="w-full max-w-4xl space-y-8 px-6">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold">Party-Umfrage: Salsa vs Bachata</h1>
          <p className="text-zinc-600">Gib deine Präferenz und Ankunftszeit an. Die Ergebnisse werden pro 15 Minuten dargestellt.</p>
        </header>

        <section className="grid gap-8 md:grid-cols-2">
          <div className="rounded-lg border bg-white p-6 shadow-sm">
            <SurveyForm />
          </div>
          <div className="rounded-lg border bg-white p-6 shadow-sm">
            <ResultsChart />
          </div>
        </section>
      </main>
    </div>
  );
}
