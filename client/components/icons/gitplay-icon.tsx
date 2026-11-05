import { SquareTerminal } from "lucide-react";

import { cn } from "@/lib/utils";

export function GitPlayIcon({ className }: { className?: string }) {
  return (
    <span className={cn("inline-flex shrink-0 items-center justify-center bg-primary text-primary-foreground", className)}>
      <SquareTerminal className="size-2/3" aria-hidden="true" />
    </span>
  );
}
