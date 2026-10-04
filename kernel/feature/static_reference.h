#ifndef KSU_STATIC_REFERENCE_H
#define KSU_STATIC_REFERENCE_H

struct selinux_policy;

#ifdef CONFIG_KSU_STATIC_SELINUX_REFERENCE
struct selinux_policy *
ksu_static_reference_create(struct selinux_policy *actual);
#endif

#endif
