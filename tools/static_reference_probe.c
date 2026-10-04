/* SPDX-License-Identifier: GPL-2.0 */
/* Read-only userspace libsepol check. No kernel policy loading or writes. */
#include <sepol/policydb.h>
#include <sepol/policydb/policydb.h>
#include <sepol/policydb/services.h>
#include <sepol/policydb/sidtab.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static sepol_policydb_t *read_policy(const char *path)
{
	sepol_policydb_t *policy = NULL;
	sepol_policy_file_t *file = NULL;
	FILE *in = fopen(path, "rb");
	if (!in || sepol_policydb_create(&policy) ||
	    sepol_policy_file_create(&file))
		exit(2);
	sepol_policy_file_set_fp(file, in);
	if (sepol_policydb_read(policy, file))
		exit(3);
	fclose(in);
	sepol_policy_file_free(file);
	return policy;
}

static int perms_match(symtab_t *left, symtab_t *right)
{
	unsigned i;
	if (left->nprim != right->nprim) {
		fprintf(stderr,
			"permission nprim differs: actual=%u reference=%u\n",
			left->nprim, right->nprim);
		return 0;
	}
	for (i = 0; i < left->table->size; ++i) {
		hashtab_ptr_t node;
		for (node = left->table->htable[i]; node; node = node->next) {
			perm_datum_t *a = node->datum;
			perm_datum_t *b =
				hashtab_search(right->table, node->key);
			if (!b || a->s.value != b->s.value) {
				fprintf(stderr,
					"permission ABI differs: %s actual=%u reference=%u\n",
					node->key, a->s.value,
					b ? b->s.value : 0);
				return 0;
			}
		}
	}
	return 1;
}

static int abi_match(policydb_t *left, policydb_t *right)
{
	unsigned i;
	if (left->policyvers != right->policyvers ||
	    left->p_classes.nprim != right->p_classes.nprim) {
		fprintf(stderr,
			"header differs: actual_version=%u reference_version=%u actual_classes=%u reference_classes=%u\n",
			left->policyvers, right->policyvers,
			left->p_classes.nprim, right->p_classes.nprim);
		return 0;
	}
	for (i = 0; i < left->p_classes.nprim; ++i) {
		class_datum_t *a = left->class_val_to_struct[i];
		class_datum_t *b = right->class_val_to_struct[i];
		if (strcmp(left->sym_val_to_name[SYM_CLASSES][i],
			   right->sym_val_to_name[SYM_CLASSES][i]) ||
		    !perms_match(&a->permissions, &b->permissions) ||
		    !!a->comdatum != !!b->comdatum ||
		    (a->comdatum && !perms_match(&a->comdatum->permissions,
						 &b->comdatum->permissions)))
			return 0;
	}
	return 1;
}

static const char *contexts[] = { "u:r:adbroot:s0",
				  "u:r:magisk:s0",
				  "u:object_r:magisk_file:s0",
				  "u:r:ksu:s0",
				  "u:object_r:ksu_file:s0",
				  "u:object_r:lsposed_file:s0",
				  "u:object_r:xposed_data:s0",
				  "u:object_r:xposed_file:s0",
				  "u:object_r:magisk_log_file:s0",
				  "u:r:magisk_file:s0",
				  "u:r:ksu_file:s0",
				  "u:r:lsposed_file:s0",
				  "u:r:xposed_data:s0" };
struct probe {
	const char *source, *target, *cls, *perm;
};
static const struct probe access[] = {
	{ "u:r:untrusted_app:s0", "u:r:untrusted_app:s0", "0", NULL },
	{ "u:r:app_zygote:s0", "u:r:app_zygote:s0", "process", "setcurrent" },
	{ "u:r:app_zygote:s0", "u:r:kernel:s0", "security", "check_context" },
	{ "u:r:system_server:s0", "u:r:system_server:s0", "process",
	  "execmem" },
	{ "u:r:shell:s0", "u:r:su:s0", "process", "transition" },
	{ "u:object_r:rootfs:s0", "u:object_r:tmpfs:s0", "filesystem",
	  "associate" },
	{ "u:r:kernel:s0", "u:object_r:tmpfs:s0", "fifo_file", "open" },
	{ "u:r:kernel:s0", "u:object_r:adb_data_file:s0", "file", "read" },
	{ "u:r:system_server:s0", "u:object_r:apk_data_file:s0", "file",
	  "execute" },
	{ "u:r:dex2oat:s0", "u:object_r:dex2oat_exec:s0", "file",
	  "execute_no_trans" },
	{ "u:r:zygote:s0", "u:object_r:adb_data_file:s0", "dir", "search" },
	{ "u:r:kernel:s0", "u:object_r:adb_data_file:s0", "file", "write" },
	{ "u:r:kernel:s0", "u:object_r:system_file:s0", "file", "write" },
	{ "u:r:kernel:s0", "u:object_r:apk_data_file:s0", "file", "write" }
};

int main(int argc, char **argv)
{
	sepol_policydb_t *reference, *actual;
	sidtab_t sidtab = { 0 };
	unsigned i;
	int matching;
	if (argc != 3) {
		fprintf(stderr,
			"usage: static_reference_probe REFERENCE.policy ACTUAL.policy\n");
		return 2;
	}
	reference = read_policy(argv[1]);
	actual = read_policy(argv[2]);
	matching = abi_match(&actual->p, &reference->p);
	if (policydb_load_isids(&reference->p, &sidtab))
		return 3;
	sepol_set_policydb(&reference->p);
	sepol_set_sidtab(&sidtab);
	printf("{\"abi_match\":%s,\"policy_version\":%u,\"classes\":%u,\"contexts\":[",
	       matching ? "true" : "false", reference->p.policyvers,
	       reference->p.p_classes.nprim);
	for (i = 0; i < sizeof(contexts) / sizeof(*contexts); ++i) {
		sepol_security_id_t sid;
		int result = sepol_context_to_sid(
			contexts[i], strlen(contexts[i]) + 1, &sid);
		printf("%s{\"id\":%u,\"context\":\"%s\",\"valid\":%s}",
		       i ? "," : "", i, contexts[i], result ? "false" : "true");
	}
	printf("],\"access\":[");
	for (i = 0; i < sizeof(access) / sizeof(*access); ++i) {
		const struct probe *test = &access[i];
		sepol_security_id_t source, target;
		sepol_security_class_t cls;
		sepol_access_vector_t permission = 0;
		struct sepol_av_decision decision = { 0 };
		const char *status = "context_invalid";
		int result = sepol_context_to_sid(
			test->source, strlen(test->source) + 1, &source);
		result = result ? result :
				  sepol_context_to_sid(test->target,
						       strlen(test->target) + 1,
						       &target);
		if (!result && !test->perm) {
			/* Kernel user-query class=0 special case, not libsepol compute_av. */
			status = "kernel_class0_model";
			decision.allowed =
				reference->p.handle_unknown == ALLOW_UNKNOWN ?
					~0U :
					0;
		} else if (!result) {
			status = "class_or_permission_invalid";
			result =
				sepol_string_to_security_class(test->cls, &cls);
			result = result ? result :
					  sepol_string_to_av_perm(
						  cls, test->perm, &permission);
			if (!result) {
				result =
					sepol_compute_av(source, target, cls,
							 permission, &decision);
				status = result ? "compute_error" : "computed";
			}
		}
		printf("%s{\"id\":%u,\"status\":\"%s\",\"allowed\":%s,\"mask\":%u,"
		       "\"requested\":%u}",
		       i ? "," : "", i, status,
		       (!result && (decision.allowed & permission)) ? "true" :
								      "false",
		       decision.allowed, permission);
	}
	printf("]}\n");
	sepol_sidtab_destroy(&sidtab);
	sepol_policydb_free(reference);
	sepol_policydb_free(actual);
	return matching ? 0 : 4;
}
