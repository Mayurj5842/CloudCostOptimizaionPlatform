"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";

const currentMonth = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
};

type MonthSelectionState = {
  selectedMonth: string;
  setSelectedMonth: (month: string) => void;
};

export const useMonthSelectionStore = create<MonthSelectionState>()(
  persist(
    (set) => ({
      selectedMonth: currentMonth(),
      setSelectedMonth: (selectedMonth) => set({ selectedMonth }),
    }),
    {
      name: "cloudcostai-selected-month",
    },
  ),
);
