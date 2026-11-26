// One-time setup: registers the global /meld slash command for your Discord application.
//   DISCORD_APP_ID=... DISCORD_BOT_TOKEN=... node scripts/register-discord-command.mjs
const appId = process.env.DISCORD_APP_ID;
const token = process.env.DISCORD_BOT_TOKEN;
if (!appId || !token) {
  console.error('Set DISCORD_APP_ID and DISCORD_BOT_TOKEN');
  process.exit(1);
}
const STRING = 3;
const BOOLEAN = 5;
const command = {
  name: 'meld',
  description: 'Start a Meld poll your server can vote on',
  options: [
    { type: STRING, name: 'question', description: 'What are you deciding?', required: true, max_length: 200 },
    { type: STRING, name: 'options', description: 'Choices separated by |  e.g. Tacos | Ramen | Pizza', required: true, max_length: 1500 },
    { type: STRING, name: 'deadline', description: 'Closes after e.g. 30m, 2h, 1d (default 24h)' },
    { type: BOOLEAN, name: 'ranked', description: 'Ranked choice (voters order their favourites)' },
    { type: BOOLEAN, name: 'multi', description: 'Allow picking several options' },
    { type: BOOLEAN, name: 'anonymous', description: 'Hide voter names' },
    { type: BOOLEAN, name: 'weekly', description: 'Open a fresh poll every week' },
  ],
};
const res = await fetch(`https://discord.com/api/v10/applications/${appId}/commands`, {
  method: 'PUT',
  headers: { authorization: `Bot ${token}`, 'content-type': 'application/json' },
  body: JSON.stringify([command]),
});
console.log(res.status, await res.text());
process.exit(res.ok ? 0 : 1);
