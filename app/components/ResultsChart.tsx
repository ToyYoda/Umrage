"use client";

import React, { useEffect, useState } from "react";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from "chart.js";
import { Line } from "react-chartjs-2";

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend);

type Results = { labels: string[]; data: number[] };

export default function ResultsChart() {
  const [results, setResults] = useState<Results>({ labels: [], data: [] });

  async function fetchResults() {
    try {
      const res = await fetch("/api/results");
      if (!res.ok) return;
      const json = await res.json();
      setResults(json);
    } catch (err) {
      console.error(err);
    }
  }

  useEffect(() => {
    fetchResults();
    const id = setInterval(fetchResults, 5000);
    return () => clearInterval(id);
  }, []);

  const data = {
    labels: results.labels,
    datasets: [
      {
        label: "% Bachata",
        data: results.data,
        borderColor: "#ef4444",
        backgroundColor: "rgba(239,68,68,0.2)",
        tension: 0.3,
      },
    ],
  };

  const options = {
    responsive: true,
    scales: {
      y: { min: 0, max: 100, ticks: { callback: (v: any) => `${v}%` } },
    },
  } as any;

  return (
    <div>
      <h2 className="mb-4 text-lg font-medium">Aktuelle Verteilung (pro 15 Minuten)</h2>
      {results.labels.length === 0 ? (
        <div className="text-sm text-zinc-500">Keine Ergebnisse vorhanden.</div>
      ) : (
        <Line options={options} data={data} />
      )}
    </div>
  );
}
