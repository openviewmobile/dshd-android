.PHONY: debug release e2e check pack-rootfs clean

debug:
	scripts/build-apk.sh

release:
	scripts/build-release-apk.sh

e2e:
	scripts/android-e2e.sh

# Structural checks that do not need a device.
check:
	bash -n scripts/*.sh
	@echo "android scripts: syntax OK"

# PACK_ROOTFS=/path/to/rootfs make pack-rootfs
pack-rootfs:
	@test -n "$(PACK_ROOTFS)" || { echo "set PACK_ROOTFS=/path/to/rootfs" >&2; exit 2; }
	scripts/pack-rootfs.sh "$(PACK_ROOTFS)" dist/dshd-rootfs-arm64.tar.gz

clean:
	rm -rf app/build app/app/build
