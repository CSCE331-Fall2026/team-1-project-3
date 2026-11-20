// Regression for #70: the menu form must keep nutrition line breaks. Run with `npm test`.
import { test, after } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "vite";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

const vite = await createServer({ server: { middlewareMode: true }, appType: "custom", logLevel: "silent" });
after(() => vite.close());
const { FormDialog } = await vite.ssrLoadModule("/src/ui.jsx");

test("multiline field renders a textarea that keeps line breaks; other fields stay inputs", () => {
  const html = renderToStaticMarkup(createElement(FormDialog, {
    title: "Edit Menu Item",
    fields: [
      { name: "name", label: "Item Name:", defaultValue: "Orange Chicken" },
      { name: "nutrition", label: "Nutrition", defaultValue: "Calories: 490\nContains: soy, wheat", multiline: true },
    ],
  }));
  assert.match(html, /<textarea name="nutrition"[^>]*>Calories: 490\nContains: soy, wheat<\/textarea>/);
  assert.match(html, /<input name="name"[^>]*value="Orange Chicken"/);
  assert.doesNotMatch(html, /<textarea name="name"/);
});
