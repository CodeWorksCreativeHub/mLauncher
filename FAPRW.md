# Fork and Pull Request Workflow (FAPRW)

This guide explains the standard workflow for contributing to a GitHub project using **forks** and **pull requests (PRs)**.

---

## 1. Fork the Repository

1. Navigate to the repository you want to contribute to.
2. Click the **Fork** button on the top-right corner of the page.
3. This creates a copy of the repository under your GitHub account.

---

## 2. Clone Your Fork Locally

Clone your fork to your local machine so you can work on it:

```
git clone https://github.com/<your-username>/mLauncher.git
cd mLauncher
```

---

## 3. Configure Upstream Remote

Set the original repository as the `upstream` remote to stay updated:

```
git remote add upstream https://github.com/CodeWorksCreativeHub/mLauncher.git
git fetch upstream
```

---

## 4. Create a New Branch

Always create a new branch for your changes:

```
git checkout -b feature/my-new-feature
```

> Replace `feature/my-new-feature` with a descriptive branch name.

---

## 5. Make Changes Locally

1. Edit files, fix bugs, or add features.
2. Stage and commit your changes:

```
git add .
git commit -m "Add a clear, descriptive commit message"
```

---

## 6. Sync Fork with Upstream

Before pushing, make sure your fork is up to date:

```
git fetch upstream
git checkout main
git merge upstream/main
```

> Resolve any merge conflicts if necessary.

---

## 7. Push Your Changes

Push your branch to your fork:

```
git push origin feature/my-new-feature
```

---

## 8. Open a Pull Request

1. Go to your fork on GitHub.
2. Click **Compare & pull request**.
3. Ensure the base repository is the original repository and the base branch is `main` (or the target branch).
4. Add a descriptive title and detailed description.
5. Submit the pull request.

---

## 9. Review and Iterate

- Collaborators may request changes.
- Make edits locally, commit, and push to the same branch.
- The PR will automatically update.

---

## 10. Merge and Cleanup

Once approved and merged:

1. Delete your feature branch locally and on your fork:

```
git branch -d feature/my-new-feature
git push origin --delete feature/my-new-feature
```

2. Sync your fork with upstream again for future contributions:

```
git fetch upstream
git checkout main
git merge upstream/main
git push origin main
```

---

## Tips for Successful Contributions

- Always pull the latest changes from upstream before starting.
- Keep commits small and focused.
- Write clear commit messages.
- Be responsive to feedback on your PR.

---

*Happy Contributing!* 🚀
