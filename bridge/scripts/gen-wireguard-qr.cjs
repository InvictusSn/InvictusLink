const fs = require("node:fs");
const path = require("node:path");
const QRCode = require("qrcode");

const confPath = process.argv[2];
const outPath = process.argv[3];
if (!confPath || !outPath) {
  console.error("Usage: node gen-wireguard-qr.cjs <config.conf> <output.png>");
  process.exit(1);
}

const text = fs.readFileSync(confPath, "utf8").replace(/^\uFEFF/, "").trim();
QRCode.toFile(outPath, text, {
  errorCorrectionLevel: "L",
  margin: 2,
  width: 480,
})
  .then(() => console.log(`QR saved: ${path.resolve(outPath)}`))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
