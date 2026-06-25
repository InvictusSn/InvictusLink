const el = (id) => document.getElementById(id);

const projectEl = el("project");
const tokenEl = el("token");
const outputStyleEl = el("outputStyle");
const promptEl = el("prompt");
const sendEl = el("send");
const statusEl = el("status");
const resultEl = el("result");

function setStatus(s) {
  statusEl.textContent = s;
}

function setResult(s) {
  resultEl.textContent = s;
}

function authHeaders() {
  const token = (tokenEl.value || "").trim();
  if (!token) return {};
  return { authorization: `Bearer ${token}` };
}

async function loadProjects() {
  const res = await fetch("/health");
  const data = await res.json();
  projectEl.innerHTML = "";
  for (const p of data.projects || []) {
    const opt = document.createElement("option");
    opt.value = p.id;
    opt.textContent = p.name;
    projectEl.appendChild(opt);
  }
}

let polling = null;
let pollingDelayMs = 1500;

async function pollTask(taskId) {
  setStatus("Running...");
  if (polling) clearInterval(polling);

  polling = setInterval(async () => {
    const res = await fetch(`/tasks/${encodeURIComponent(taskId)}`);
    const data = await res.json();
    if (data.status === "completed") {
      clearInterval(polling);
      polling = null;
      pollingDelayMs = 1500;
      setStatus("Done.");
      setResult(data.output || "");
      sendEl.disabled = false;
      return;
    }
    if (data.status === "error") {
      clearInterval(polling);
      polling = null;
      pollingDelayMs = 1500;
      setStatus("Error.");
      setResult(data.error || "Unknown error");
      sendEl.disabled = false;
      return;
    }
    if (data.status) {
      setStatus(`Status: ${data.status}...`);
    }
  }, pollingDelayMs);
}

async function submit() {
  const prompt = (promptEl.value || "").trim();
  if (!prompt) {
    setStatus("Type a prompt first.");
    return;
  }

  const projectId = projectEl.value || "test";
  const outputStyle = outputStyleEl.value || "short";

  sendEl.disabled = true;
  setResult("");
  setStatus("Submitting...");

  const res = await fetch("/tasks", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...authHeaders(),
    },
    body: JSON.stringify({ prompt, projectId, outputStyle }),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    setStatus("Request failed.");
    setResult(err.error || `HTTP ${res.status}`);
    sendEl.disabled = false;
    return;
  }

  const data = await res.json();
  const taskId = data.taskId;
  await pollTask(taskId);
}

sendEl.addEventListener("click", () => void submit());
promptEl.addEventListener("keydown", (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
    void submit();
  }
});

loadProjects().catch((e) => {
  setStatus("Failed to load projects.");
  setResult(String(e));
});

setStatus("Idle.");

