# Secret Rotation Required

## Summary

The `.env` file was tracked in Git history since the initial commit (`7c5951e`). This file may contain credentials that are now exposed in the repository history.

## Services Potentially Exposed

- **Evolution API**: API key, instance name, base URL
- **AI Service**: Model configuration (no secrets)
- **Spring Application**: Profile configuration (no secrets)

## Credentials That Need Rotation

1. **Evolution API Key** (`EVOLUTION_API_KEY`)
   - Present in `.env` file committed to Git
   - Still present in Git history (cannot be removed without rewriting history)
   - **Must be rotated** at the Evolution API provider

2. **Evolution Instance Name** (`EVOLUTION_INSTANCE_NAME`)
   - Low sensitivity, but should be reviewed

## Safe Rotation Order

1. Generate new API key in Evolution API dashboard
2. Update the key in all deployment environments (production, staging, local)
3. Verify the new key works by sending a test message
4. Revoke the old API key after verification

## Validations After Rotation

- Send a test WhatsApp message via the API
- Verify the application starts without authentication errors
- Check application logs for any authentication failures

## Git History Note

The `.env` file remains in Git history. To fully remove it, a `git filter-branch` or `git filter-repo` operation would be required, which rewrites history and forces all collaborators to re-clone. This is **not recommended** unless there is evidence the credentials were compromised.

## Current Status

- `.env` has been removed from Git tracking (`git rm --cached`)
- `.env` is now in `.gitignore`
- `.env.example` has been created with placeholder values
- The file remains in Git history for forensic review if needed