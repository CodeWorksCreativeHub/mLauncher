#!/usr/bin/env node

const https = require("https");
const { execSync } = require("child_process");

const WEBHOOK_URL = process.env.DISCORD_WEBHOOK_URL;
const REPO_URL = "https://github.com/DroidWorksStudio/mLauncher";

// Commit parsing rules
const commitParsers = [
	// Skip some "noise" commits
	{ message: /^chore\(release\): prepare for/i, skip: true },
	{ message: /^chore\(deps.*\)/i, skip: true },
	{ message: /^chore\(change.*\)/i, skip: true },
	{ message: /^(lang|i18n)/i, skip: true },
	{ message: /^chore\(pr\)/i, skip: true },
	{ message: /^chore\(pull\)/i, skip: true },
	{ message: /^fixes/i, skip: true },

	// Enhancements (new features, improvements, UX, performance)
	{ message: /^feat|^perf|^style|^ui|^ux/i, group: "### :sparkles: Enhancements:" },

	// Bug fixes & hotfixes
	{ message: /^fix|^bug|^hotfix|^emergency/i, group: "### :bug: Bug Fixes:" },

	// Code quality (refactors, cleanup without changing behavior)
	{ message: /^refactor/i, group: "### :wrench: Code Quality:" },

	// Documentation
	{ message: /^doc/i, group: "### :books: Documentation:" },

	// Security
	{ message: /^security/i, group: "### :lock: Security:" },

	// Feature removal / drops
	{ message: /^drop|^remove|^deprecated/i, group: "### :x: Feature Removals:" },

	// Reverts
	{ message: /^revert/i, group: "### :rewind: Reverts:" },

	// Build-related
	{ message: /^build/i, group: "### :building_construction: Build:" },

	// Dependencies-related
	{ message: /^dependency|^deps/i, group: "### :package: Dependencies:" },

	// Meta: configuration, CI/CD, versioning, releases
	{ message: /^config|^configuration|^ci|^pipeline|^release|^version|^versioning/i, group: "### :gear: Meta:" },

	// Tests
	{ message: /^test/i, group: "### :test_tube: Tests:" },

	// Infrastructure & Ops
	{ message: /^infra|^infrastructure|^ops/i, group: "### :office: Infrastructure & Ops:" },

	// Chore & cleanup
	{ message: /^chore|^housekeeping|^cleanup|^clean\(up\)/i, group: "### :broom: Maintenance & Cleanup:" },
];

const GROUP_ORDER = commitParsers.filter((p) => !p.skip).map((p) => p.group);

function run(cmd) {
	return execSync(cmd, { encoding: "utf8" }).trim();
}

function cleanMessage(message) {
	// Remove conventional commit type (feat, fix, etc.), with optional scope (...) and colon
	return message.replace(/^(feat|fix|fixed|bug|lang|i18n|doc|docs|perf|refactor|style|ui|ux|security|revert|release|dependency|deps|build|ci|pipeline|chore|housekeeping|version|versioning|config|configuration|cleanup|clean\(up\)|drop|remove|deprecated|hotfix|emergency|test|infra|infrastructure|ops|asset|content|exp|experiment|prototype)\s*(\(.+?\))?:\s*/i, "");
}

function linkPR(message) {
	return message.replace(/\(#(\d+)\)/g, (_, num) => ``);
}

function classifyCommit(message) {
	for (const parser of commitParsers) {
		if (parser.message.test(message)) {
			if (parser.skip) return null;
			return parser.group;
		}
	}
	return null;
}

function getGitHubReleaseTitle(tag, callback) {
    const options = {
        hostname: "api.github.com",
        path: `/repos/DroidWorksStudio/mLauncher/releases/tags/${tag}`,
        method: "GET",
        headers: {
            "User-Agent": "Node.js",
            "Accept": "application/vnd.github.v3+json"
        },
    };

    const req = https.request(options, (res) => {
        let data = "";
        res.on("data", (chunk) => data += chunk);
        res.on("end", () => {
            if (res.statusCode >= 200 && res.statusCode < 300) {
                const json = JSON.parse(data);
                callback(null, json.name || tag); // release title
            } else {
                callback(new Error(`GitHub API returned ${res.statusCode}: ${data}`));
            }
        });
    });

    req.on("error", (e) => callback(e));
    req.end();
}

// Get latest tag
const allTags = run("git tag --sort=-creatordate").split("\n");
const latestTag = allTags[0];
const previousTag = allTags[1];
const range = previousTag ? `${previousTag}..${latestTag}` : latestTag;

// Get commits
const rawCommits = run(`git log ${range} --pretty=format:"%h|%s"`).split("\n");
const commits = rawCommits
	.map((line) => {
		const [hash, ...msgParts] = line.split("|");
		const message = msgParts.join("|").trim();
		const group = classifyCommit(message);
		if (!group) return null;
		return { group, message: linkPR(cleanMessage(message)), hash };
	})
	.filter(Boolean);

// Group commits
const groups = {};
for (const c of commits) {
	groups[c.group] = groups[c.group] || [];
	groups[c.group].push(`* ${c.message}`);
}

// Build plain message
// Fetch release title and then build/send Discord message
getGitHubReleaseTitle(latestTag, (err, releaseTitle) => {
    if (err) return console.error("Error fetching release:", err);

    // Build Discord message AFTER fetching release title
    let discordMessage = `## ${releaseTitle}\n\n`;

    for (const group of GROUP_ORDER) {
        if (!groups[group] || groups[group].length === 0) continue;
        discordMessage += `${group}\n${groups[group].join("\n")}\n\n`;
    }

    if (!commits.length) discordMessage += "No commits found.";

    discordMessage += `<@&${process.env.DISCORD_ROLEID}>\n\n`;
    discordMessage += `:arrow_down:  [Direct APK Download](${REPO_URL}/releases/download/${latestTag}/MultiLauncher-${latestTag}-Signed.apk)  :arrow_down:\n\n`;

    const payload = JSON.stringify({
        content: discordMessage,
        username: "Multi Launcher Updates",
        avatar_url: "https://github.com/DroidWorksStudio/mLauncher/blob/main/fastlane/metadata/android/en-US/images/icon.png?raw=true",
    });

    const url = new URL(WEBHOOK_URL);
    const options = {
        hostname: url.hostname,
        path: url.pathname + url.search,
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Content-Length": payload.length,
        },
    };

    const req = https.request(options, (res) => {
        let data = "";
        res.on("data", (chunk) => (data += chunk));
        res.on("end", () => {
            if (res.statusCode >= 200 && res.statusCode < 300) {
                console.log("✅ Release posted to Discord!");
            } else {
                console.error("Failed to post:", res.statusCode, data);
            }
        });
    });

    req.on("error", (e) => console.error("Error:", e));
    req.write(payload);
    req.end();
});
