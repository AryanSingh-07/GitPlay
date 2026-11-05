"use client";

import { RequireAuth } from "@/components/providers/require-auth";
import { AppShell } from "@/components/layouts/app-shell";
import { SettingsDashboard } from "@/components/dashboard/settings-dashboard";

export default function DashboardPage() {
  return (
    <RequireAuth>
      <AppShell title="Settings" description="Manage your profile and appearance">
        <SettingsDashboard />
      </AppShell>
    </RequireAuth>
  );
}
    
