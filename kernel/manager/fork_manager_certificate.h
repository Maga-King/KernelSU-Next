/* SPDX-License-Identifier: GPL-2.0 */
#ifndef KSU_FORK_MANAGER_CERTIFICATE_H
#define KSU_FORK_MANAGER_CERTIFICATE_H

#include <linux/string.h>
#include <linux/types.h>

/* Public certificate only. The private key must never enter this repository. */
#define KSU_FORK_MANAGER_CERT_SIZE 805U
#define KSU_FORK_MANAGER_CERT_SHA256                                           \
	"72e4f93179c5e4811d570608a40803877a80a98b1635f0ff847115f454d3c6c2"

static inline bool ksu_manager_cert_size_allowed(unsigned int size,
						 unsigned int expected_size)
{
	return size == expected_size || size == KSU_FORK_MANAGER_CERT_SIZE;
}

static inline bool ksu_manager_cert_allowed(unsigned int size, const char *hash,
					    unsigned int expected_size,
					    const char *expected_hash)
{
	/* Size and digest must match the SAME entry; never mix the two entries. */
	return (size == expected_size && !strcmp(hash, expected_hash)) ||
	       (size == KSU_FORK_MANAGER_CERT_SIZE &&
		!strcmp(hash, KSU_FORK_MANAGER_CERT_SHA256));
}

#endif /* KSU_FORK_MANAGER_CERTIFICATE_H */
