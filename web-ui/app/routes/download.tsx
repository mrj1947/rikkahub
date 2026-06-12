import { Cloud, Download, Smartphone, Check, ExternalLink } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { Route } from "./+types/download";
import { Button } from "~/components/ui/button";

export const meta: Route.MetaFunction = () => [
  { title: "Download RikkaHub" },
  { name: "description", content: "Download RikkaHub - A native Android LLM chat client" },
];

const APK_RELEASES = [
  {
    name: "RikkaHub (Latest)",
    version: "v1.0.0",
    size: "150MB",
    description: "Latest stable release with all features",
    downloadUrl: "https://github.com/rikkahub/rikkahub/releases/latest",
    type: "direct",
    platforms: ["arm64-v8a", "armeabi-v7a", "x86_64"],
  },
  {
    name: "Google Play Store",
    version: "Latest",
    size: "Varies",
    description: "Install from Google Play Store for automatic updates",
    downloadUrl: "https://play.google.com/store/apps/details?id=me.rerere.rikkahub",
    type: "store",
    icon: "play",
  },
  {
    name: "Official Website",
    version: "Latest",
    size: "Varies",
    description: "Download from the official RikkaHub website",
    downloadUrl: "https://rikka-ai.com/download",
    type: "website",
    icon: "globe",
  },
];

const FEATURES = [
  "🤖 Support for 20+ AI providers (OpenAI, Claude, Gemini, DeepSeek, etc.)",
  "🎨 Material You Design with dark mode support",
  "🖼️ Multimodal input (Text, Images, PDF, DOCX, PPTX, EPUB)",
  "🔄 Message branching for alternative responses",
  "🛠️ MCP (Model Context Protocol) support",
  "📝 Markdown rendering with code highlighting & LaTeX",
  "🔍 Web search integration",
  "🧠 ChatGPT-like memory features",
  "⚙️ Customizable system prompts and parameters",
];

const SYSTEM_REQUIREMENTS = [
  { label: "Android Version", value: "Android 7.0 and above" },
  { label: "Processor", value: "ARM64, ARMv7, or x86_64" },
  { label: "RAM", value: "4GB minimum (8GB recommended)" },
  { label: "Storage", value: "200MB free space" },
  { label: "Internet", value: "Required for AI provider communication" },
];

export default function Download() {
  const { t } = useTranslation();

  return (
    <div className="min-h-screen bg-gradient-to-b from-background via-background to-muted/10">
      {/* Header */}
      <div className="border-b border-border/40 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="flex items-center gap-3 mb-4">
            <Smartphone className="w-8 h-8 text-primary" />
            <h1 className="text-4xl font-bold text-foreground">RikkaHub</h1>
          </div>
          <p className="text-lg text-muted-foreground">
            A native Android LLM chat client supporting 20+ AI providers
          </p>
        </div>
      </div>

      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        {/* Download Section */}
        <section className="mb-16">
          <h2 className="text-3xl font-bold mb-8 text-foreground">Download Options</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {APK_RELEASES.map((release, idx) => (
              <div
                key={idx}
                className="rounded-lg border border-border/50 bg-card p-6 hover:border-primary/50 hover:shadow-lg transition-all duration-300"
              >
                <div className="flex items-start justify-between mb-4">
                  <div>
                    <h3 className="text-xl font-semibold text-foreground">{release.name}</h3>
                    <p className="text-sm text-muted-foreground mt-1">{release.version}</p>
                  </div>
                  {release.type === "direct" && <Download className="w-6 h-6 text-primary" />}
                  {release.type === "store" && <Cloud className="w-6 h-6 text-primary" />}
                  {release.type === "website" && <ExternalLink className="w-6 h-6 text-primary" />}
                </div>

                <p className="text-sm text-muted-foreground mb-2">{release.description}</p>

                {release.type === "direct" && release.platforms && (
                  <div className="mb-4 text-xs text-muted-foreground">
                    <p className="font-medium mb-2">Supported Architectures:</p>
                    <ul className="space-y-1">
                      {release.platforms.map((platform) => (
                        <li key={platform} className="flex items-center gap-2">
                          <Check className="w-3 h-3 text-green-600" />
                          {platform}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                <div className="mb-4 text-xs text-muted-foreground">
                  <span className="font-medium">Size: </span>
                  {release.size}
                </div>

                <Button
                  asChild
                  className="w-full"
                  size="lg"
                  variant={idx === 0 ? "default" : "outline"}
                >
                  <a
                    href={release.downloadUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center justify-center gap-2"
                  >
                    <Download className="w-4 h-4" />
                    {release.type === "direct" ? "Download APK" : "Download"}
                  </a>
                </Button>
              </div>
            ))}
          </div>
        </section>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-12">
          {/* Features Section */}
          <section>
            <h2 className="text-3xl font-bold mb-8 text-foreground">✨ Features</h2>
            <div className="space-y-4">
              {FEATURES.map((feature, idx) => (
                <div key={idx} className="flex items-start gap-3">
                  <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-1" />
                  <span className="text-foreground/90">{feature}</span>
                </div>
              ))}
            </div>
          </section>

          {/* System Requirements Section */}
          <section>
            <h2 className="text-3xl font-bold mb-8 text-foreground">📋 System Requirements</h2>
            <div className="space-y-4 bg-muted/50 rounded-lg p-6 border border-border/50">
              {SYSTEM_REQUIREMENTS.map((req, idx) => (
                <div key={idx} className="flex items-center justify-between py-3 border-b border-border/30 last:border-b-0">
                  <span className="text-foreground font-medium">{req.label}</span>
                  <span className="text-muted-foreground text-sm">{req.value}</span>
                </div>
              ))}
            </div>
          </section>
        </div>

        {/* Installation Instructions */}
        <section className="mt-16 bg-card border border-border/50 rounded-lg p-8">
          <h2 className="text-3xl font-bold mb-6 text-foreground">🚀 Installation Instructions</h2>
          <div className="space-y-6">
            <div>
              <h3 className="text-xl font-semibold text-foreground mb-3 flex items-center gap-2">
                <span className="flex items-center justify-center w-8 h-8 rounded-full bg-primary text-primary-foreground font-bold">1</span>
                Enable Unknown Sources
              </h3>
              <p className="text-foreground/80 ml-10">
                Go to Settings → Security → Enable "Unknown Sources" (or "Install from unknown sources" on newer Android versions)
              </p>
            </div>
            <div>
              <h3 className="text-xl font-semibold text-foreground mb-3 flex items-center gap-2">
                <span className="flex items-center justify-center w-8 h-8 rounded-full bg-primary text-primary-foreground font-bold">2</span>
                Download the APK
              </h3>
              <p className="text-foreground/80 ml-10">
                Click on the download button above to get the latest RikkaHub APK file
              </p>
            </div>
            <div>
              <h3 className="text-xl font-semibold text-foreground mb-3 flex items-center gap-2">
                <span className="flex items-center justify-center w-8 h-8 rounded-full bg-primary text-primary-foreground font-bold">3</span>
                Install the APK
              </h3>
              <p className="text-foreground/80 ml-10">
                Locate the downloaded APK file in your file manager and tap to install
              </p>
            </div>
            <div>
              <h3 className="text-xl font-semibold text-foreground mb-3 flex items-center gap-2">
                <span className="flex items-center justify-center w-8 h-8 rounded-full bg-primary text-primary-foreground font-bold">4</span>
                Launch the App
              </h3>
              <p className="text-foreground/80 ml-10">
                Find RikkaHub in your app drawer and open it to get started
              </p>
            </div>
          </div>
        </section>

        {/* Support Section */}
        <section className="mt-16 bg-muted/50 rounded-lg p-8 border border-border/50">
          <h2 className="text-2xl font-bold mb-6 text-foreground">📞 Support & Community</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Button
              asChild
              variant="outline"
              className="h-auto py-4"
            >
              <a
                href="https://discord.gg/9weBqxe5c4"
                target="_blank"
                rel="noopener noreferrer"
                className="flex flex-col items-center gap-2"
              >
                <span className="text-lg font-semibold">Join Discord</span>
                <span className="text-sm text-muted-foreground">Get help and stay updated</span>
              </a>
            </Button>
            <Button
              asChild
              variant="outline"
              className="h-auto py-4"
            >
              <a
                href="https://github.com/rikkahub/rikkahub"
                target="_blank"
                rel="noopener noreferrer"
                className="flex flex-col items-center gap-2"
              >
                <span className="text-lg font-semibold">GitHub Repository</span>
                <span className="text-sm text-muted-foreground">View source code & issues</span>
              </a>
            </Button>
          </div>
        </section>
      </div>
    </div>
  );
}
