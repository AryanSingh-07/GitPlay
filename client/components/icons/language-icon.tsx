import { Code2 } from "lucide-react";
import type { IconType } from "react-icons/lib";
import {
  SiGo,
  SiJavascript,
  SiOpenjdk,
  SiPython,
  SiRust,
  SiTypescript,
} from "react-icons/si";

import { cn } from "@/lib/utils";

type LanguageConfig = {
  Icon: IconType;
  bg: string;
  iconClass: string;
};

const LANGUAGE_MAP: Record<string, LanguageConfig> = {
  JavaScript: { Icon: SiJavascript, bg: "bg-[#F7DF1E]", iconClass: "text-[#323330]" },
  TypeScript: { Icon: SiTypescript, bg: "bg-[#3178C6]", iconClass: "text-white" },
  Python: { Icon: SiPython, bg: "bg-[#3776AB]", iconClass: "text-white" },
  Java: { Icon: SiOpenjdk, bg: "bg-[#ED8B00]", iconClass: "text-white" },
  Go: { Icon: SiGo, bg: "bg-[#00ADD8]", iconClass: "text-white" },
  Rust: { Icon: SiRust, bg: "bg-[#A85D3B]", iconClass: "text-white" },
};

export function getLanguageLabel(language: string | null) {
  return language || "Unknown";
}

export function LanguageIcon({
  language,
  size = "md",
  className,
}: {
  language: string | null;
  size?: "sm" | "md" | "lg";
  className?: string;
}) {
  const config = language ? LANGUAGE_MAP[language] : undefined;
  const Icon = config?.Icon ?? Code2;

  return (
    <span
      title={getLanguageLabel(language)}
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-md",
        config?.bg ?? "bg-muted",
        config?.iconClass ?? "text-muted-foreground",
        size === "sm" && "size-5 [&>svg]:size-3",
        size === "md" && "size-9 [&>svg]:size-5",
        size === "lg" && "size-12 [&>svg]:size-7",
        className,
      )}
    >
      <Icon aria-hidden="true" />
    </span>
  );
}
