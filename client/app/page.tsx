import Link from "next/link";
import { ArrowRight, FolderGit2 } from "lucide-react";

import { GitPlayIcon } from "@/components/icons/gitplay-icon";
import { BrandMark } from "@/components/layouts/app-shell";
import { ModeToggle } from "@/components/ui/mode-toggle";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { getGithubLoginUrl } from "@/lib/api";

export default function HomePage() {
  return (
    <div className="relative min-h-svh overflow-hidden">
      {/* Background Radial Gradient */}
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-muted/50 via-background to-background" />
      
      {/* Navigation Header */}
      <header className="relative z-10 mx-auto flex h-14 w-full max-w-5xl items-center justify-between px-4">
        <BrandMark />
        <div className="flex items-center gap-2">
          <ModeToggle />
          <Link
            href="/login"
            className={cn(buttonVariants({ variant: "ghost", size: "sm" }))}
          >
            Sign in
          </Link>
        </div>
      </header>

      {/* Main Hero Layout */}
      <main className="relative z-10 mx-auto flex max-w-3xl flex-col items-center justify-center px-4 pt-20 text-center sm:pt-32">
        <div className="mb-6 flex items-center justify-center">
          <GitPlayIcon className="size-14 rounded-2xl" />
        </div>
        
        <div className="space-y-3">
          <h1 className="font-heading text-4xl font-semibold tracking-tight sm:text-5xl">
            GitPlay
          </h1>
          <p className="max-w-2xl text-lg text-muted-foreground text-balance">
            Know your code
          </p>
        </div>

        {/* Action Call-to-Action Buttons */}
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <a
            href={getGithubLoginUrl()}
            className={cn(
              buttonVariants({ size: "lg" }),
              "inline-flex items-center gap-1.5"
            )}
          >
            <FolderGit2 className="size-4" />
            Continue with GitHub
            <ArrowRight className="size-4" />
          </a>
          
          <Link
            href="/login"
            className={cn(buttonVariants({ variant: "outline", size: "lg" }))}
          >
            Alternative Sign In
          </Link>
        </div>
      </main>
    </div>
  );
}
