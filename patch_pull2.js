const fs = require('fs');
const { execSync } = require('child_process');

try {
  execSync('git fetch origin main');
  execSync('git reset --hard origin/main');
} catch (e) {
  console.log(e.toString());
}
