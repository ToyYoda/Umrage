import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Garage 61 - Schnellste Runde Spa-Francorchamps",
  description: "Zeigt deine schnellste Runde auf Spa-Francorchamps aus Garage 61",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="de">
      <body className="antialiased">{children}</body>
    </html>
  );
}
