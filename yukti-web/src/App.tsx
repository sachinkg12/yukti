import { Routes, Route } from "react-router-dom"
import Home from "./pages/Home"
import Results from "./pages/Results"

export default function App() {
  return (
    <div className="flex min-h-screen flex-col antialiased">
      <div
        className="shrink-0 bg-amber-200 py-2 text-center text-sm font-medium text-amber-900"
        role="status"
        aria-live="polite"
      >
        Yukti is AI and can make mistakes.
      </div>
      <main className="flex-1">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/results" element={<Results />} />
        </Routes>
      </main>
      <footer className="shrink-0 border-t border-amber-300 bg-amber-200 py-3 text-center text-sm font-medium text-amber-900">
        Yukti is AI and can make mistakes.
      </footer>
    </div>
  )
}
