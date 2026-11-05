import type { Metadata } from "next";
import { Geist, Geist_Mono, JetBrains_Mono, IBM_Plex_Sans } from "next/font/google";
import "./globals.css";
import { cn } from "@/lib/utils";
import { ThemeProvider } from "@/components/providers/theme-provider";
import QueryProvider from "@/components/providers/query-provider";
import { Toaster } from "@/components/ui/toast";

const ibmPlexSansHeading = IBM_Plex_Sans({subsets:['latin'],variable:'--font-ibm-plex-sans'});

const jetbrainsMono = JetBrains_Mono({subsets:['latin'],variable:'--font-jetbrains-mono'});

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "GitPlay",
  description: "Know your code. Chat with your GitHub repositories using answers grounded in your code.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={cn("h-full", "antialiased", geistSans.variable, geistMono.variable, "font-mono", jetbrainsMono.variable, ibmPlexSansHeading.variable)}
    >
      <body className="min-h-full flex flex-col">
        <QueryProvider>
          <ThemeProvider
            attribute="class"
            defaultTheme="system"
            enableSystem
            disableTransitionOnChange
          >
            <Toaster>{children}</Toaster>
          </ThemeProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
