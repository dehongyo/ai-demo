import { Routes, Route } from "react-router-dom";
import SessionPage from "./pages/SessionPage";
import "./index.css";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<SessionPage />} />
      <Route path="/sessions/:sessionId" element={<SessionPage />} />
    </Routes>
  );
}
