#!/usr/bin/env node

const https = require("https");
const { execSync } = require("child_process");

const WEBHOOK_URL = process.env.DISCORD_WEBHOOK_URL;
const REPO_URL = "https://github.com/CodeWorksCreativeHub/mLauncher";

// Commit parsing rules
const commitParsers = [
	// Skip "noise" commits
	{ message: /^chore\(release\): prepare for/i, skip: true },
	{ message: /^chore\(deps.*\)/i, skip: true },
	{ message: /^chore\(change.*\)/i, skip: true },
	{ message: /^(lang|i18n)/i, skip: true },
	{ message: /^chore\(pr\)/i, skip: true },
	{ message: /^chore\(pull\)/i, skip: true },
	{ message: /^fixes/i, skip: true },
	{ message: /^build/i, skip: true },

	// Enhancements
	{ message: /^feat|^perf|^style|^ui|^ux/i, group: "### :sparkles: Enhancements:" },

	// Bug fixes
	{ message: /^fix|^bug|^hotfix|^emergency/i, group: "### :bug: Bug Fixes:" },

	// Code quality
	{ message: /^refactor/i, group: "### :wrench: Code Quality:" },

	// Documentation
	{ message: /^doc/i, group: "### :books: Documentation:" },

	// Security
	{ message: /^security/i, group: "### :lock: Security:" },

	// Feature removal
	{ message: /^drop|^remove|^deprecated/i, group: "### :x: Feature Removals:" },

	// Reverts
	{ message: /^revert/i, group: "### :rewind: Reverts:" },

	// Build
	{ message: /^build/i, group: "### :building_construction: Build:" },

	// Dependencies
	{ message: /^dependency|^deps/i, group: "### :package: Dependencies:" },

	// Meta
	{ message: /^config|^configuration|^ci|^pipeline|^release|^version|^versioning/i, group: "### :gear: Meta:" },

	// Tests
	{ message: /^test/i, group: "### :test_tube: Tests:" },

	// Infrastructure
	{ message: /^infra|^infrastructure|^ops/i, group: "### :office: Infrastructure & Ops:" },

	// Chore & cleanup
	{ message: /^chore|^housekeeping|^cleanup|^clean\(up\)/i, group: "### :broom: Maintenance & Cleanup:" },
];

const GROUP_ORDER = commitParsers.filter((p) => !p.skip).map((p) => p.group);

// Utility functions
function run(cmd) {
	return execSync(cmd, { encoding: "utf8" }).trim();
}

function cleanMessage(message) {
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

// Get the latest stable releases from GitHub
function getReleases() {
	const options = {
		hostname: "api.github.com",
		path: "/repos/CodeWorksCreativeHub/mLauncher/releases",
		method: "GET",
		headers: { "User-Agent": "Node.js" },
	};

	return new Promise((resolve, reject) => {
		const req = https.request(options, (res) => {
			let data = "";
			res.on("data", (chunk) => (data += chunk));
			res.on("end", () => {
				try {
					const releases = JSON.parse(data);
					// Only non-prerelease, non-draft releases
					const stable = releases.filter((r) => !r.prerelease && !r.draft);
					if (stable.length < 1) return reject(new Error("No stable releases found"));
					resolve(stable);
				} catch (e) {
					reject(e);
				}
			});
		});
		req.on("error", reject);
		req.end();
	});
}

function formatLauncherVersion(input) {
	// Extract version inside parentheses: (1.11.2.6)
	const versionMatch = input.match(/\(([\d.]+)\)/);
	if (!versionMatch) return null;

	const fullVersion = versionMatch[1]; // "1.11.2.6"
	const versionParts = fullVersion.split("."); // ["1","11","2","6"]
	const build = versionParts.pop(); // "6"
	const version = versionParts.join("."); // "1.11.2"

	// Extract the app name before the "‧"
	const nameMatch = input.match(/-\s*(.*?)\s*‧/);
	const appName = nameMatch ? nameMatch[1] : "Unknown";

	return `${appName} ${version} Build ${build}`;
}

// Main async function
(async () => {
	try {
		const releases = await getReleases();
		const latestRelease = releases[0];
		const previousRelease = releases[1]; // may be undefined

		const latestTag = latestRelease.tag_name;
		const previousTag = previousRelease ? previousRelease.tag_name : null;

		// Commit range
		const range = previousTag ? `${previousTag}..${latestTag}` : latestTag;
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

		// Group commits by category
		const groups = {};
		for (const c of commits) {
			groups[c.group] = groups[c.group] || [];
			groups[c.group].push(`* ${c.message}`);
		}

		// Use release title from API response
		const releaseTitle = latestRelease.name || latestRelease.tag_name;

		// Build Discord message
		let discordMessage = `## ${formatLauncherVersion(releaseTitle)}\n\n`;

		for (const group of GROUP_ORDER) {
			if (!groups[group] || groups[group].length === 0) continue;
			discordMessage += `${group}\n${groups[group].join("\n")}\n\n`;
		}

		if (!commits.length) discordMessage += "No commits found.";

		discordMessage += `<@&${process.env.DISCORD_ROLEID}>\n\n`;
		discordMessage += `:arrow_down:  [Direct APK Download](${REPO_URL}/releases/download/${latestTag}/MultiLauncher-${latestTag}-Signed.apk)  :arrow_down:\n\n`;

		// Debug: Show message length
		console.log(`Message length: ${discordMessage.length} characters`);

		// Check if message exceeds Discord's 2000 character limit
		if (discordMessage.length > 2000) {
			console.log("Truncating message to fit Discord limit...");

			// Start with header
			const header = `## ${formatLauncherVersion(releaseTitle)}\n\n`;
			const footer = `<@&${process.env.DISCORD_ROLEID}>\n\n:arrow_down:  [Direct APK Download](${REPO_URL}/releases/download/${latestTag}/MultiLauncher-${latestTag}-Signed.apk)  :arrow_down:\n\n`;

			let truncatedMessage = header;
			let currentLength = header.length + footer.length + 100; // Leave buffer for omission notice

			// Add commits while staying under limit
			for (const group of GROUP_ORDER) {
				if (!groups[group] || groups[group].length === 0) continue;

				const groupHeader = `${group}\n`;
				const groupCommits = groups[group].join("\n") + "\n\n";
				const groupTotal = groupHeader + groupCommits;

				if (currentLength + groupTotal.length > 1900) {
					// Calculate how many commits we're showing vs omitting
					const commitsShown = truncatedMessage.split("\n").filter((line) => line.startsWith("*")).length;
					const commitsOmitted = commits.length - commitsShown;

					truncatedMessage += `\n... (${commitsOmitted} more commits omitted due to length limit)\n\n`;
					break;
				}

				truncatedMessage += groupTotal;
				currentLength += groupTotal.length;
			}

			// Add footer
			truncatedMessage += footer;

			console.log(`Truncated message length: ${truncatedMessage.length} characters`);
			discordMessage = truncatedMessage;
		}

		// Send to Discord
		const payload = JSON.stringify({
			content: discordMessage,
			username: "Multi Launcher Updates",
			avatar_url: "https://github.com/CodeWorksCreativeHub/mLauncher/blob/main/fastlane/metadata/android/en-US/images/icon.png?raw=true",
		});

		const url = new URL(WEBHOOK_URL);
		const options = {
			hostname: url.hostname,
			path: url.pathname + url.search,
			method: "POST",
			headers: {
				"Content-Type": "application/json",
				"Content-Length": Buffer.byteLength(payload),
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
	} catch (err) {
		console.error("Error:", err);
	}
})();
