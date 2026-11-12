import { defineTool } from "@deepseek-ai/dsh-tools";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

export const name = "android-bridge";
export const inject = ["tools"];

const OUTPUT = {
  type: "object",
  additionalProperties: true
};

function credentials() {
  const home = process.env.HOME || os.homedir();
  const file = path.join(home, ".dsh", "android-bridge.json");
  let data;
  try {
    data = JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    throw new Error(
      `Android bridge is unavailable (${file}). Open the DSHD app so the Host service starts, then retry. ${error}`
    );
  }
  if (!data?.url || !data?.token) throw new Error(`Android bridge credentials are invalid: ${file}`);
  return data;
}

async function call(method, endpoint, body) {
  const bridge = credentials();
  const response = await fetch(bridge.url + endpoint, {
    method,
    headers: {
      "X-DSHD-Token": bridge.token,
      "Content-Type": "application/json"
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(25_000)
  });
  const text = await response.text();
  let value;
  try {
    value = JSON.parse(text);
  } catch {
    throw new Error(`Android bridge returned HTTP ${response.status}: ${text.slice(0, 300)}`);
  }
  return value;
}

function render(_args, value) {
  return [{ type: "text", text: JSON.stringify(value, null, 2) }];
}

function tool(name, description, parameters, run) {
  return defineTool({
    name,
    description,
    parameters,
    output: { schema: OUTPUT, render },
    timeoutMs: 30_000,
    execute: run
  });
}

export function apply(ctx) {
  ctx.tools.register(tool(
    "android_permissions",
    "List Android runtime permissions available to this device and whether each is granted. "
      + "Use before location, Bluetooth, call, contacts or notification actions.",
    {},
    () => call("GET", "/v1/permissions")
  ));

  ctx.tools.register(tool(
    "android_request_permission",
    "Open the Android permission center and ask the user to grant one permission. "
      + "This only opens the UI; the user must tap Grant. Verify with android_permissions afterwards.",
    {
      name: {
        type: "string",
        required: true,
        enum: ["microphone", "camera", "location", "notifications", "bluetooth", "phone", "contacts"],
        description: "Permission to request."
      }
    },
    (args) => call("POST", "/v1/permissions/request", { name: args.name })
  ));

  ctx.tools.register(tool(
    "android_location",
    "Get a one-shot foreground location fix from the phone. Requires location permission. "
      + "No background tracking and no continuous updates.",
    {},
    () => call("GET", "/v1/location")
  ));

  ctx.tools.register(tool(
    "android_bluetooth_status",
    "Show Bluetooth adapter state and bonded devices. Requires Bluetooth permission.",
    {},
    () => call("GET", "/v1/bluetooth")
  ));

  ctx.tools.register(tool(
    "android_bluetooth_scan",
    "Scan for nearby Bluetooth Low Energy devices for a few seconds. "
      + "Requires Bluetooth permission; Android 11 and older also require location.",
    {
      seconds: {
        type: "integer",
        description: "Scan duration between 3 and 15 seconds. Defaults to 8."
      }
    },
    (args) => call("POST", "/v1/bluetooth/scan", { seconds: args.seconds ?? 8 })
  ));

  ctx.tools.register(tool(
    "android_contacts_search",
    "Search the phone's contacts by name or number. Read-only; requires contacts permission.",
    {
      query: {
        type: "string",
        required: true,
        description: "Name fragment or phone number fragment."
      },
      limit: {
        type: "integer",
        description: "Maximum results, 1-50. Defaults to 20."
      }
    },
    (args) => call("GET", `/v1/contacts?query=${encodeURIComponent(args.query)}&limit=${args.limit ?? 20}`)
  ));

  ctx.tools.register(tool(
    "android_phone_call",
    "Place a real phone call immediately on the phone. Requires the phone-call permission. "
      + "Only call when the user explicitly asked to call this number; never guess a number.",
    {
      number: {
        type: "string",
        required: true,
        description: "Phone number with optional +, spaces, - or parentheses."
      }
    },
    (args) => call("POST", "/v1/phone/call", { number: args.number })
  ));

  ctx.tools.register(tool(
    "android_notify",
    "Post a local Android notification to the user. Requires notification permission on Android 13+.",
    {
      title: { type: "string", required: true, description: "Notification title." },
      body: { type: "string", description: "Notification body." }
    },
    (args) => call("POST", "/v1/notify", { title: args.title, body: args.body ?? "" })
  ));
}
