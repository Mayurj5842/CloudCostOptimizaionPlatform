"use client";

import RecommendationsPage from "@/features/recommendations/recommendations-page";


export default function RecommendationPanel({ month }: { month: string }) {
  return <RecommendationsPage compact month={month} />;
}
