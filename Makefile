# Owner shortcuts: build a branch and install it on the phone.
#
#   make install-dev      build origin/dev  and install on the phone
#   make install-main     build origin/main and install on the phone
#   make install BRANCH=feat/x   any branch
#   make apk-dev | apk-main      build only, print the APK path
#   make devices          list connected phones
#   make logs             app logs from the phone (own process only), Ctrl+C to stop
#
# It builds in its own worktree (~/phone-agent-worktrees/install-<branch>) and never touches
# your working folders. Gradle runs through ~/.gradle-agents/gw (max two builds at once on this
# machine, own GRADLE_USER_HOME "owner"). Several phones connected: make install-dev DEVICE=<serial>.
SHELL := /bin/bash
REPO := $(CURDIR)
WT_DIR := $(HOME)/phone-agent-worktrees
ADB := $(or $(shell command -v adb),$(HOME)/Library/Android/sdk/platform-tools/adb)
GW := $(HOME)/.gradle-agents/gw owner
PACKAGE := com.example.phoneagent
DEVICE ?=
ADB_S := $(if $(DEVICE),-s $(DEVICE),)
OUT := $(HOME)/phone-agent-builds

.PHONY: install-dev install-main install apk-dev apk-main devices logs

install-dev: BRANCH := dev
install-main: BRANCH := main
install-dev install-main install: apk
	@apk="$$(ls -t $(OUT)/phone-agent-$(subst /,-,$(BRANCH))-*-debug.apk | head -1)"; \
	echo "installing $$apk"; \
	$(ADB) $(ADB_S) install -r "$$apk" && echo "OK: installed $$(basename $$apk)"

apk-dev: BRANCH := dev
apk-main: BRANCH := main
apk-dev apk-main: apk

.PHONY: apk
apk:
	@test -n "$(BRANCH)" || { echo "give BRANCH=..."; exit 1; }
	@set -e; wt="$(WT_DIR)/install-$(subst /,-,$(BRANCH))"; \
	git -C $(REPO) fetch -q origin "$(BRANCH)"; \
	if [ -d "$$wt" ]; then git -C "$$wt" checkout -q --detach "origin/$(BRANCH)"; \
	else git -C $(REPO) worktree add -q --detach "$$wt" "origin/$(BRANCH)"; fi; \
	[ -f "$$wt/local.properties" ] || cp $(REPO)/local.properties "$$wt/local.properties"; \
	sha="$$(git -C "$$wt" rev-parse --short HEAD)"; \
	echo "building $(BRANCH) @ $$sha"; \
	( cd "$$wt" && $(GW) :app:assembleDebug ); \
	mkdir -p $(OUT); \
	cp "$$wt/app/build/outputs/apk/debug/app-debug.apk" "$(OUT)/phone-agent-$(subst /,-,$(BRANCH))-$$sha-debug.apk"; \
	echo "APK: $(OUT)/phone-agent-$(subst /,-,$(BRANCH))-$$sha-debug.apk"

devices:
	@$(ADB) devices -l

logs:
	@pid="$$($(ADB) $(ADB_S) shell pidof $(PACKAGE) | tr -d '\r')"; \
	test -n "$$pid" || { echo "app is not running on the phone"; exit 1; }; \
	$(ADB) $(ADB_S) logcat --pid="$$pid" -v time
