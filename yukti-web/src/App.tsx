import { Routes, Route } from "react-router-dom"
import Home from "./pages/Home"
import Results from "./pages/Results"

export default function App() {
  return (
    <div className="min-h-screen antialiased">
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/results" element={<Results />} />
        </Routes>
      </main>
    </div>
  )
}
