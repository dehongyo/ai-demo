import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import App from "../App";

function renderApp(route = "/") {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <App />
    </MemoryRouter>,
  );
}

describe("App", () => {
  it("renders the welcome message on home page", () => {
    renderApp("/");
    expect(screen.getByText(/Minimal Agent/i)).toBeInTheDocument();
  });

  it("renders sidebar with new session button", () => {
    renderApp("/");
    expect(screen.getByText("+ 新建")).toBeInTheDocument();
  });
});
