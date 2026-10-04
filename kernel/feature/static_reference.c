// SPDX-License-Identifier: GPL-2.0
/* Query-only reference. Never install this policy in selinux_state.policy. */
#include <linux/err.h>
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/version.h>
#include <security.h>
#include <ss/policydb.h>
#include <ss/services.h>
#include "klog.h"
#include "selinux/sepolicy.h"
#include "static_reference.h"
#include "static_reference_blob.h"

static void *reference_symtab_search(struct symtab *table, const char *name)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)
	return symtab_search(table, name);
#else
	return hashtab_search(table->table, name);
#endif
}

/* Raw selinuxfs class/permission numbers come from the active policy.
 * A fixed reference must use exactly the same ABI. Fail back to upstream
 * when it does not, rather than returning decisions for the wrong class.
 */
static bool permissions_match(struct symtab *actual, struct symtab *reference)
{
	struct hashtab_node *node;
	u32 i;

	if (actual->nprim != reference->nprim)
		return false;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 8, 0)
	for (i = 0; i < actual->table.size; ++i) {
		node = actual->table.htable[i];
#else
	if (!actual->table || !reference->table)
		return actual->table == reference->table;
	for (i = 0; i < actual->table->size; ++i) {
		node = actual->table->htable[i];
#endif
		for (; node; node = node->next) {
			struct perm_datum *left = node->datum;
			struct perm_datum *right =
				reference_symtab_search(reference, node->key);
			if (!right || left->value != right->value)
				return false;
		}
	}
	return true;
}

static bool classes_match(struct policydb *actual, struct policydb *reference)
{
	u32 i;

	if (actual->policyvers != reference->policyvers ||
	    actual->p_classes.nprim != reference->p_classes.nprim)
		return false;
	for (i = 0; i < actual->p_classes.nprim; ++i) {
		struct class_datum *left = actual->class_val_to_struct[i];
		struct class_datum *right = reference->class_val_to_struct[i];
		const char *left_name = actual->sym_val_to_name[SYM_CLASSES][i];
		const char *right_name =
			reference->sym_val_to_name[SYM_CLASSES][i];

		if (!left || !right || !left_name || !right_name ||
		    strcmp(left_name, right_name) ||
		    !permissions_match(&left->permissions, &right->permissions))
			return false;
		if (!!left->comdatum != !!right->comdatum)
			return false;
		if (left->comdatum &&
		    !permissions_match(&left->comdatum->permissions,
				       &right->comdatum->permissions))
			return false;
	}
	return true;
}

struct selinux_policy *
ksu_static_reference_create(struct selinux_policy *actual)
{
	struct selinux_policy *reference;
	struct policy_file file = {
		.data = (void *)ksu_static_reference_blob,
		.len = sizeof(ksu_static_reference_blob),
	};
	int ret;

	/* Application queries use raw policy class numbers, not the enforcement
     * class map. Do not borrow any allocation from the active policy.
     * Existing backup initialization creates a private sidtab afterwards.
     */
	reference = kzalloc(sizeof(*reference), GFP_KERNEL);
	if (!reference)
		return ERR_PTR(-ENOMEM);
	reference->latest_granting = actual->latest_granting;

	ret = policydb_read(&reference->policydb, &file);
	if (ret) {
		/* policydb_read unwinds its partially-created policy on failure. */
		kfree(reference);
		return ERR_PTR(ret);
	}
	reference->policydb.len = sizeof(ksu_static_reference_blob);
	if (!classes_match(&actual->policydb, &reference->policydb)) {
		pr_warn("static reference: class/permission ABI mismatch; use upstream backup\n");
		ksu_destroy_sepolicy(reference);
		return ERR_PTR(-EPROTONOSUPPORT);
	}
	pr_info("static query reference accepted: %zu bytes, sha256=%s\n",
		sizeof(ksu_static_reference_blob), KSU_STATIC_REFERENCE_SHA256);
	return reference;
}
