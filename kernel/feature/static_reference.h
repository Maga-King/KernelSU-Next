#ifndef KSU_STATIC_REFERENCE_H
#define KSU_STATIC_REFERENCE_H

#include <linux/err.h>
#include "klog.h"
#include "selinux/sepolicy.h"

#ifdef CONFIG_KSU_STATIC_SELINUX_REFERENCE
struct selinux_policy *
ksu_static_reference_create(struct selinux_policy *actual);
#endif

/* Keep the optional reference and fallback out of the upstream rules flow. */
static inline struct selinux_policy *
ksu_create_query_reference(struct selinux_policy *actual)
{
#ifdef CONFIG_KSU_STATIC_SELINUX_REFERENCE
	struct selinux_policy *reference = ksu_static_reference_create(actual);

	if (!IS_ERR(reference))
		return reference;
	pr_warn("static query reference unavailable (%ld), falling back to upstream backup\n",
		PTR_ERR(reference));
#endif
	return ksu_dup_sepolicy(actual);
}

#endif
