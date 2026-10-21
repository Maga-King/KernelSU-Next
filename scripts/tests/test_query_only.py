"""Execute the production query parser/converter against independent policies.

The small host harness replaces kernel allocation and SELinux services, not the
functions under test. It cannot validate kernel hooks or device specialization.
"""

import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'kernel/feature/selinux_hide.c'


def extract_function(source, name):
    match = re.search(r'static (?:int|bool|ssize_t)\s+' + re.escape(name) + r'\([^;{]*\)\s*\{', source)
    if not match:
        raise AssertionError(f'Production function definition missing: {name}')
    depth = 1
    end = match.end()
    while depth and end < len(source):
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    if depth:
        raise AssertionError(f'Unterminated function: {name}')
    return source[match.start():end]


HARNESS = r'''
#include <assert.h>
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

typedef uint32_t u32;
typedef unsigned int gfp_t;
#define SECSID_NULL 0
#define GFP_KERNEL 123

struct user_datum { u32 value; };
struct role_datum { u32 value; };
struct type_datum { u32 value; int attribute; };
struct entry { const char *name; void *datum; };
struct symtab { struct entry *entries; size_t count; };
struct policydb { struct symtab p_users, p_roles, p_types; };
struct context { u32 user, role, type; void *mls; int initialized; };
struct sidtab { unsigned int writes; u32 next_sid; };
struct selinux_policy { struct policydb policydb; struct sidtab *sidtab; };

static struct user_datum ref_user = {7}, live_user = {101};
static struct role_datum ref_role = {17}, live_role = {107};
static struct type_datum ref_type = {23, 0}, ref_only = {29, 0};
static struct type_datum ref_attr = {31, 1};
static struct type_datum live_type = {113, 0}, live_only = {127, 0};
static struct entry ref_users[] = {{"u", &ref_user}};
static struct entry ref_roles[] = {{"r", &ref_role}};
static struct entry ref_types[] = {
    {"shared", &ref_type}, {"reference_only", &ref_only}, {"attribute", &ref_attr}
};
static struct entry live_users[] = {{"u", &live_user}};
static struct entry live_roles[] = {{"r", &live_role}};
static struct entry live_types[] = {{"shared", &live_type}, {"live_only", &live_only}};
static struct sidtab ref_sids = {0, 41}, live_sids = {0, 900};
static struct selinux_policy reference = {
    {{ref_users, 1}, {ref_roles, 1}, {ref_types, 3}}, &ref_sids
};
static struct selinux_policy live = {
    {{live_users, 1}, {live_roles, 1}, {live_types, 2}}, &live_sids
};
static int allocation_failure, mls_failure, invalid_context, sid_failure;
static unsigned int allocations, frees, initializations, destructions, mls_calls;
static struct context recorded;

static void *symtab_search(struct symtab *table, const char *name)
{
    size_t i;
    for (i = 0; i < table->count; ++i)
        if (!strcmp(table->entries[i].name, name))
            return table->entries[i].datum;
    return NULL;
}

static char *kmemdup_nul(const char *input, u32 len, gfp_t flags)
{
    char *copy;
    assert(flags == GFP_KERNEL);
    if (allocation_failure)
        return NULL;
    copy = malloc(len + 1);
    assert(copy);
    memcpy(copy, input, len);
    copy[len] = '\0';
    ++allocations;
    return copy;
}

static void kfree(void *pointer)
{
    if (pointer) {
        ++frees;
        free(pointer);
    }
}

static void context_init(struct context *ctx)
{
    memset(ctx, 0, sizeof(*ctx));
    ctx->initialized = 1;
    ++initializations;
}

static void context_destroy(struct context *ctx)
{
    assert(ctx->initialized == 1);
    kfree(ctx->mls);
    ctx->mls = NULL;
    ctx->initialized = 0;
    ++destructions;
}

static int mls_context_to_sid(struct policydb *pol, char delimiter, char *tail,
                             struct context *ctx, struct sidtab *table, u32 def_sid)
{
    assert(pol == &reference.policydb);
    assert(table == &ref_sids);
    assert(def_sid == 55);
    ++mls_calls;
    /* Even a partially failed MLS parse must release its allocation. */
    ctx->mls = malloc(8);
    assert(ctx->mls);
    ++allocations;
    if (mls_failure)
        return mls_failure;
    if (delimiter != ':')
        return -EINVAL;
    return strcmp(tail, "s0") ? -EINVAL : 0;
}

static int policydb_context_isvalid(struct policydb *pol, struct context *ctx)
{
    assert(pol == &reference.policydb);
    assert(ctx->user == 7 && ctx->role == 17);
    assert(ctx->type == 23 || ctx->type == 29);
    return !invalid_context;
}

static int sidtab_context_to_sid(struct sidtab *table, struct context *ctx, u32 *sid)
{
    assert(table == &ref_sids);
    assert(ctx->user == 7 && ctx->role == 17);
    recorded = *ctx;
    if (sid_failure)
        return sid_failure;
    ++table->writes;
    *sid = table->next_sid++;
    return 0;
}

/* PRODUCTION_FUNCTIONS */

int main(int argc, char **argv)
{
    char input[80], before[80];
    const char *context = "u:r:shared:s0";
    int expected = 0, rc;
    unsigned int expected_init = 1, expected_mls = 1;
    u32 sid = 999, len, expected_type = 23;
    struct sidtab live_before = live_sids;
    struct policydb live_policy_before = live.policydb;
    assert(argc == 2);
    if (!strcmp(argv[1], "shared")) { }
    else if (!strcmp(argv[1], "reference_only")) {
        context = "u:r:reference_only:s0";
        expected_type = 29;
    } else if (!strcmp(argv[1], "live_only")) {
        context = "u:r:live_only:s0"; expected = -EINVAL; expected_mls = 0;
        assert(symtab_search(&live.policydb.p_types, "live_only"));
    } else if (!strcmp(argv[1], "unknown_user")) {
        context = "unknown:r:shared:s0"; expected = -EINVAL; expected_mls = 0;
    } else if (!strcmp(argv[1], "unknown_role")) {
        context = "u:unknown:shared:s0"; expected = -EINVAL; expected_mls = 0;
    } else if (!strcmp(argv[1], "unknown_type")) {
        context = "u:r:unknown:s0"; expected = -EINVAL; expected_mls = 0;
    } else if (!strcmp(argv[1], "attribute")) {
        context = "u:r:attribute:s0"; expected = -EINVAL; expected_mls = 0;
    } else if (!strcmp(argv[1], "missing_user_delimiter")) {
        context = "u"; expected = -EINVAL; expected_mls = 0;
    } else if (!strcmp(argv[1], "missing_role_delimiter")) {
        context = "u:r"; expected = -EINVAL; expected_mls = 0;
    } else if (!strcmp(argv[1], "missing_mls")) {
        context = "u:r:shared"; expected = -EINVAL;
    } else if (!strcmp(argv[1], "invalid_mls")) {
        context = "u:r:shared:s99"; expected = -EINVAL;
    } else if (!strcmp(argv[1], "mls_failure")) {
        mls_failure = expected = -ENOMEM;
    } else if (!strcmp(argv[1], "validation_failure")) {
        invalid_context = 1; expected = -EINVAL;
    } else if (!strcmp(argv[1], "sidtab_failure")) {
        sid_failure = expected = -ENOSPC;
    } else if (!strcmp(argv[1], "allocation_failure")) {
        allocation_failure = 1; expected = -ENOMEM; expected_init = expected_mls = 0;
    } else if (!strcmp(argv[1], "empty")) {
        context = ""; expected = -EINVAL; expected_init = expected_mls = 0;
    } else { assert(!"Unknown test case"); }

    memset(input, 0x5a, sizeof(input));
    len = strlen(context);
    memcpy(input, context, len);
    /* No terminator within the supplied length: only the copy may be parsed. */
    memcpy(before, input, sizeof(input));
    rc = security_context_to_sid_with_policy(&reference, input, len, &sid, 55, GFP_KERNEL);
    assert(rc == expected);
    assert(!memcmp(input, before, sizeof(input)));
    assert(initializations == expected_init && destructions == expected_init);
    assert(allocations == frees);
    assert(mls_calls == expected_mls);
    assert(!memcmp(&live_sids, &live_before, sizeof(live_sids)));
    assert(!memcmp(&live.policydb, &live_policy_before, sizeof(live_policy_before)));
    assert(live_user.value == 101 && live_role.value == 107 && live_type.value == 113);
    if (!expected) {
        assert(sid == 41 && ref_sids.writes == 1);
        assert(recorded.user == 7 && recorded.role == 17 && recorded.type == expected_type);
    } else {
        assert(sid == SECSID_NULL && ref_sids.writes == 0);
    }
    return 0;
}
'''


SCOPE_HARNESS = r'''
#define _POSIX_C_SOURCE 200809L
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <errno.h>
typedef uint32_t u32;
#define PER_USER_RANGE 100000
#define SYM_TYPES 3
struct context { u32 type; unsigned int len; };
struct type_datum { u32 value; int attribute; };
struct symtab { u32 nprim; };
struct ebitmap { unsigned int bits; };
struct policydb { struct symtab p_types; struct ebitmap *type_attr_map_array; };
struct sidtab { int unused; };
struct selinux_policy { struct policydb policydb; struct sidtab *sidtab; };
struct kuid { u32 val; };
static struct kuid caller_uid = {10001};
static struct context caller_context = {3, 0};
static struct type_datum attribute = {7, 1};
static struct type_datum app_type = {3, 0}, zygote_type = {4, 0}, root_type = {5, 0};
static struct ebitmap maps[8] = {{0}, {0}, {1U << 6}};
static struct sidtab live_sids;
static struct selinux_policy active = {{ {8}, maps }, &live_sids};
static struct { struct selinux_policy *policy; } selinux_state = {&active};
static const char *domain = "untrusted_app";
static int depth, missing_context, missing_attribute;
static struct kuid current_uid(void) { return caller_uid; }
static void rcu_read_lock(void) { assert(depth == 0); ++depth; }
static void rcu_read_unlock(void) { assert(depth == 1); --depth; }
#define rcu_dereference(pointer) (assert(depth == 1), (pointer))
static u32 current_sid(void) { return 513; }
static struct context *sidtab_search(struct sidtab *table, u32 sid)
{
    assert(depth == 1 && table == &live_sids && sid == 513);
    return missing_context ? NULL : &caller_context;
}
static const char *sym_name(struct policydb *db, int kind, u32 index)
{
    assert(depth == 1 && db == &active.policydb && kind == SYM_TYPES && index == 2);
    return domain;
}
static struct type_datum *symtab_search(struct symtab *table, const char *name)
{
    assert(depth == 1 && table == &active.policydb.p_types);
    if (!strcmp(name, "appdomain")) return missing_attribute ? NULL : &attribute;
    if (!strcmp(name, "live_app")) return &app_type;
    if (!strcmp(name, "app_zygote")) return &zygote_type;
    if (!strcmp(name, "ksu") || !strcmp(name, "su") || !strcmp(name, "ksu_file"))
        return &root_type;
    return NULL;
}
static int ebitmap_get_bit(struct ebitmap *map, unsigned int bit)
{
    assert(depth == 1 && map >= maps && map < maps + 8 && bit == 6);
    return !!(map->bits & (1U << bit));
}
#define GFP_KERNEL 123
#define SECSID_NULL 0
#define SECINITSID_SECURITY 2
#define SECCLASS_SECURITY 3
#define SECURITY__CHECK_CONTEXT 4
#define SIMPLE_TRANSACTION_LIMIT 4096
#define pr_err(...) ((void)0)
struct file { int unused; };
static int oom, allocations, frees, native_calls, reference_calls, denied;
static struct selinux_policy private_reference;
static struct selinux_policy *backup_sepolicy = &private_reference;
static char *kmemdup_nul(const char *input, size_t size, unsigned int flags)
{
    char *copy;
    assert(flags == GFP_KERNEL && depth == 0);
    if (oom) return NULL;
    copy = malloc(size + 1); assert(copy);
    memcpy(copy, input, size); copy[size] = '\0'; ++allocations;
    return copy;
}
static void kfree(void *p) { if (p) { free(p); ++frees; } }
static ssize_t orig_context_write(struct file *file, char *buf, size_t size)
{
    size_t len = strnlen(buf, size);
    (void)file;
    assert(depth == 0); ++native_calls;
    if ((len == strlen("u:r:live_app:s0") && !memcmp(buf, "u:r:live_app:s0", len)) ||
        (len == strlen("u:r:app_zygote:s0") && !memcmp(buf, "u:r:app_zygote:s0", len)) ||
        (len == strlen("u:r:ksu:s0") && !memcmp(buf, "u:r:ksu:s0", len)))
        return 19;
    return -EINVAL;
}
static int ksu_avc_has_perm_compat(u32 sid, int target, int class, int perm, void *audit)
{
    assert(sid == 513 && target == 2 && class == 3 && perm == 4 && !audit);
    return denied ? -EACCES : 0;
}
static int security_context_to_sid_with_policy(struct selinux_policy *policy, const char *buf,
                                              u32 size, u32 *sid, u32 def, unsigned int flags)
{
    (void)buf; (void)size; (void)sid;
    assert(policy == &private_reference && def == 0 && flags == GFP_KERNEL);
    ++reference_calls;
    return -EINVAL; /* These probes are intentionally absent from the reference. */
}
static int security_sid_to_context_with_policy(struct selinux_policy *policy, u32 sid,
                                              char **buf, u32 *size)
{
    (void)policy; (void)sid; (void)buf; (void)size;
    assert(!"Rejected reference contexts cannot be canonicalized");
    return -EINVAL;
}
/* PRODUCTION_FUNCTIONS */
static void route(const char *input, size_t size, bool native, ssize_t expected)
{
    char buf[128], before[128];
    int old_native = native_calls, old_reference = reference_calls;
    assert(size <= sizeof(buf));
    memset(buf, 0x5a, sizeof(buf)); memcpy(buf, input, size); memcpy(before, buf, sizeof(buf));
    assert(my_write_context(NULL, buf, size) == expected);
    assert(native_calls == old_native + native);
    assert(reference_calls == old_reference + (!native && !denied));
    assert(allocations == frees && depth == 0);
    assert(!memcmp(buf, before, sizeof(buf)));
}
#define ROUTE(input, native, expected) route(input, sizeof(input) - 1, native, expected)
static void check(bool expected)
{
    struct context before = caller_context;
    struct policydb policy_before = active.policydb;
    struct ebitmap maps_before[8];
    memcpy(maps_before, maps, sizeof(maps));
    assert(depth == 0);
    assert(should_spoof_query() == expected);
    assert(depth == 0);
    assert(!memcmp(&before, &caller_context, sizeof(before)));
    assert(!memcmp(&policy_before, &active.policydb, sizeof(policy_before)));
    assert(!memcmp(maps_before, maps, sizeof(maps)));
}
int main(void)
{
    const char *zygotes[] = {"zygote", "webview_zygote", "vendor_zygote64"};
    unsigned int i;
    check(true);
    /* UID is already the app UID but the SELinux transition has not happened. */
    for (i = 0; i < sizeof(zygotes) / sizeof(zygotes[0]); ++i) {
        domain = zygotes[i];
        check(false); /* Deliberately still marked appdomain, as app_zygote can be. */
    }
    domain = "app_zygote"; check(true);
    domain = "vendor_app_zygote"; check(true);
    maps[2].bits = 0; check(true); maps[2].bits = 1U << 6;
    domain = "untrusted_app";
    check(true); /* The same UID becomes eligible only after specialization. */
    caller_uid.val = 0; check(false);
    caller_uid.val = 1000; check(false);
    caller_uid.val = 101000; check(false);
    caller_uid.val = 110001; check(true);
    caller_uid.val = 199001; check(true);
    caller_uid.val = 10001;
    domain = "vendor_service";
    maps[2].bits = 0; check(false); maps[2].bits = 1U << 6;
    domain = "untrusted_app";
    selinux_state.policy = NULL; check(false); selinux_state.policy = &active;
    active.sidtab = NULL; check(false); active.sidtab = &live_sids;
    missing_context = 1; check(false); missing_context = 0;
    caller_context.len = 9; check(false); caller_context.len = 0;
    caller_context.type = 0; check(false);
    caller_context.type = 9; check(false); caller_context.type = 3;
    active.policydb.type_attr_map_array = NULL; check(false);
    active.policydb.type_attr_map_array = maps;
    domain = NULL; check(false); domain = "untrusted_app";
    missing_attribute = 1; check(false); missing_attribute = 0;
    attribute.attribute = 0; check(false); attribute.attribute = 1;
    attribute.value = 0; check(false); attribute.value = 7;
    check(true);
    domain = "app_zygote";
    ROUTE("u:r:live_app:s0", true, 19);
    ROUTE("u:r:app_zygote:s0", true, 19);
    ROUTE("u:bad:live_app:s0", true, -EINVAL);
    ROUTE("u:r:live_app:s99", true, -EINVAL);
    ROUTE("bad:r:live_app:s0", true, -EINVAL);
    ROUTE("u:r:live_app", true, -EINVAL);
    ROUTE("u:r:ksu:s0", false, -EINVAL);
    ROUTE("u:r:su:s0", false, -EINVAL);
    ROUTE("u:r:ksu_file:s0", false, -EINVAL);
    ROUTE("u:r:sentinel:s0", false, -EINVAL);
    ROUTE("u:r:appdomain:s0", false, -EINVAL);
    ROUTE("u:r:ksu\0:live_app:s0", false, -EINVAL);
    ROUTE("u:r:live_app\0:ksu:s0", true, -EINVAL);
    ROUTE("u:r:", false, -EINVAL);
    ROUTE("u", false, -EINVAL);
    ROUTE("", false, -EINVAL);
    oom = 1; ROUTE("u:r:ksu:s0", true, 19); oom = 0;
    missing_attribute = 1; ROUTE("u:r:live_app:s0", true, 19); missing_attribute = 0;
    app_type.value = 0; ROUTE("u:r:live_app:s0", true, 19); app_type.value = 3;
    denied = 1; ROUTE("u:r:ksu:s0", false, -EACCES); denied = 0;
    domain = "zygote"; ROUTE("u:r:ksu:s0", true, 19);
    return 0;
}
'''


class QueryOnlyTests(unittest.TestCase):
    def test_no_enforcement_hook_or_live_policy_mutation(self):
        source = SOURCE.read_text(encoding='utf-8')
        source = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.DOTALL)
        # These are architectural boundaries, not implementation snapshots.
        for forbidden in ('setprocattr', 'ksu_lsm_hook', 'ksu_lsm_unhook',
                          'prepare_creds', 'commit_creds', 'rcu_assign_pointer'):
            with self.subTest(forbidden=forbidden):
                self.assertIsNone(re.search(r'\b' + re.escape(forbidden) + r'\b', source),
                                  f'Forbidden enforcement dependency: {forbidden}')
        self.assertIsNone(re.search(r'\bselinux_state\.policy\s*=(?!=)', source))
        for name in ('string_to_context_struct', 'security_context_to_sid_with_policy'):
            self.assertNotIn('selinux_state', extract_function(source, name))
        self.assertNotIn('context_needs_native_validation', extract_function(source, 'my_write_access'))
        self.assertIn('should_spoof_query()', extract_function(source, 'my_write_access'))
        self.assertIn('should_spoof_query()', extract_function(source, 'my_sel_open_handle_status'))

    def test_scope_keeps_app_zygote_probes_and_context_routing_preserves_validation(self):
        compiler = shutil.which('cc') or shutil.which('clang') or shutil.which('gcc')
        if not compiler:
            self.skipTest('A host C compiler is required')
        source = SOURCE.read_text(encoding='utf-8')
        functions = '\n\n'.join(extract_function(source, name) for name in (
            'should_spoof_query', 'context_needs_native_validation', 'my_write_context'))
        harness = SCOPE_HARNESS.replace('/* PRODUCTION_FUNCTIONS */', functions)
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            (path / 'scope.c').write_text(harness, encoding='utf-8')
            subprocess.run([compiler, '-std=c11', '-Wall', '-Wextra', '-Werror',
                            '-fsanitize=undefined', '-fno-sanitize-recover=all',
                            str(path / 'scope.c'), '-o', str(path / 'scope')],
                           check=True, capture_output=True, text=True)
            result = subprocess.run([str(path / 'scope')], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_production_converter_uses_private_policy_and_cleans_up(self):
        compiler = shutil.which('cc') or shutil.which('clang') or shutil.which('gcc')
        if not compiler:
            self.skipTest('A host C compiler is required')
        source = SOURCE.read_text(encoding='utf-8')
        functions = '\n\n'.join(extract_function(source, name) for name in (
            'string_to_context_struct', 'security_context_to_sid_with_policy'))
        harness = HARNESS.replace('/* PRODUCTION_FUNCTIONS */', functions)
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            (path / 'query.c').write_text(harness, encoding='utf-8')
            subprocess.run([compiler, '-std=c11', '-Wall', '-Wextra', '-Werror',
                            '-fsanitize=undefined', '-fno-sanitize-recover=all',
                            str(path / 'query.c'), '-o', str(path / 'query')],
                           check=True, capture_output=True, text=True)
            for case in ('shared', 'reference_only', 'live_only', 'unknown_user',
                         'unknown_role', 'unknown_type', 'attribute',
                         'missing_user_delimiter', 'missing_role_delimiter',
                         'missing_mls', 'invalid_mls', 'mls_failure',
                         'validation_failure', 'sidtab_failure',
                         'allocation_failure', 'empty'):
                with self.subTest(case=case):
                    result = subprocess.run([str(path / 'query'), case],
                                            capture_output=True, text=True)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
