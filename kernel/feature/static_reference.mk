# Fork-only build rules. Keep the main Kbuild integration to one include.
ifeq ($(CONFIG_KSU_STATIC_SELINUX_REFERENCE),y)
ifdef KBUILD_EXTMOD
ccflags-y += -DCONFIG_KSU_STATIC_SELINUX_REFERENCE=1
endif
kernelsu-objs += feature/static_reference.o
ccflags-y += -I$(obj)/feature
KSU_REFERENCE_POLICY ?= $(KSU_KERNEL_DIR)/feature/reference.policy
PYTHON3 ?= python3
targets += feature/static_reference_blob.h
quiet_cmd_ksu_reference = GEN     $@
      cmd_ksu_reference = $(PYTHON3) $(KSU_KERNEL_DIR)/../scripts/embed_static_reference.py $(KSU_REFERENCE_POLICY) $@
$(obj)/feature/static_reference_blob.h: $(KSU_REFERENCE_POLICY) $(KSU_KERNEL_DIR)/../scripts/embed_static_reference.py FORCE
	$(call if_changed,ksu_reference)
$(obj)/feature/static_reference.o: $(obj)/feature/static_reference_blob.h
endif
