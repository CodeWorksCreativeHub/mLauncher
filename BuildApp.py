import os
import subprocess
import sys

build_type = sys.argv[1] if len(sys.argv) > 1 else "Prod"
print(f"Building {build_type} version...")

required_vars = ["KEY_STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"]
for var in required_vars:
    if not os.getenv(var):
        print(f"ERROR: {var} is not set!")
        sys.exit(1)

print(f"Using signing key: {os.getenv('KEY_ALIAS')}")


def run(cmd):
    subprocess.check_call(cmd, shell=True)


run(f"gradlew clean")
run(f"gradlew assemble{build_type}Release --no-configuration-cache --refresh-dependencies")
run(f"gradlew bundle{build_type}Release --no-configuration-cache --refresh-dependencies")

apk_path = f"app/build/outputs/apk/{build_type}/release/app-{build_type}-release.apk".lower()
bundle_path = f"app/build/outputs/bundle/{build_type}Release/app-{build_type}-release.aab".lower()

if os.path.exists(apk_path):
    print(f"APK built successfully: {apk_path}")
else:
    print(f"APK not found: {apk_path}")

if os.path.exists(bundle_path):
    print(f"Bundle built successfully: {bundle_path}")
else:
    print(f"Bundle not found: {bundle_path}")

print("Build finished.")
