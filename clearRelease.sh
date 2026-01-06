#!/usr/bin/env bash
set -e

echo "=== Fetching all releases ==="
curl -sSf -H "Authorization: Bearer $GITHUB_TOKEN" \
  "https://api.github.com/repos/${GITHUB_REPOSITORY}/releases" | jq -r '.[] | "\(.id)\t\(.tag_name)"'

echo "=== Getting nightly releases ==="
old_releases=$(curl -sSf -H "Authorization: Bearer $GITHUB_TOKEN" \
  "https://api.github.com/repos/${GITHUB_REPOSITORY}/releases" | \
  jq -r '.[] | select(.tag_name | startswith("nightly-")) | "\(.id)\t\(.tag_name)"')

if [ -z "$old_releases" ]; then
  echo "No nightly releases found. Exiting."
  exit 0
fi

echo "=== Looping through nightly releases ==="
echo "$old_releases" | while IFS=$'\t' read -r release_id tag_name; do
  echo "Preparing to delete release: ID=$release_id, Tag=$tag_name"

  # Check release URL
  release_url="https://api.github.com/repos/${GITHUB_REPOSITORY}/releases/${release_id}"
  echo "Release URL: $release_url"
  curl -v -H "Authorization: Bearer $GITHUB_TOKEN" "$release_url"

  # Delete release
  echo "Deleting release..."
  curl -v -sSf -H "Authorization: Bearer $GITHUB_TOKEN" -X DELETE "$release_url"

  # Check tag URL
  tag_url="https://api.github.com/repos/${GITHUB_REPOSITORY}/git/refs/tags/${tag_name}"
  echo "Tag URL: $tag_url"
  curl -v -H "Authorization: Bearer $GITHUB_TOKEN" "$tag_url"

  # Delete tag
  echo "Deleting tag..."
  curl -v -sSf -H "Authorization: Bearer $GITHUB_TOKEN" -X DELETE "$tag_url"

  echo "---"
done
