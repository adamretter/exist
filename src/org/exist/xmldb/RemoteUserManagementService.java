/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb;

import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Stream;
import org.exist.EXistException;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.Account;
import org.exist.security.User;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import org.exist.security.ACLPermission;
import org.exist.security.AXSchemaType;
import org.exist.security.EXistSchemaType;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SchemaType;
import org.exist.security.internal.aider.ACEAider;

/**
 * @author Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 */
public class RemoteUserManagementService extends AbstractRemote implements EXistUserManagementService {

    public RemoteUserManagementService(final RemoteCollection collection) {
        super(collection);
    }

    @Override
    public String getName() {
        return "UserManagementService";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void addAccount(final Account user) throws XMLDBException {
        try {
            final Map<String, String> metadata = new HashMap<>();
            for (final SchemaType key : user.getMetadataKeys()) {
                metadata.put(key.getNamespace(), user.getMetadataValue(key));
            }
            collection.getClient().addAccount(
                    user.getName(),
                    user.getPassword() == null ? "" : user.getPassword(),
                    user.getDigestPassword() == null ? "" : user.getDigestPassword(),
                    Arrays.asList(user.getGroups()),
                    user.isEnabled(),
                    user.getUserMask(),
                    metadata
                    );
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void addGroup(final Group role) throws XMLDBException {
        try {
            //TODO what about group managers?
            final Map<String, String> metadata = new HashMap<>();
            for (final SchemaType key : role.getMetadataKeys()) {
                metadata.put(key.getNamespace(), role.getMetadataValue(key));
            }

            collection.getClient().addGroup(role.getName(), metadata);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void setUserPrimaryGroup(final String username, final String groupName) throws XMLDBException {
        try {
            collection.getClient().setUserPrimaryGroup(username, groupName);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    private List<ACEAider> getACEs(final Permission perm) {
        final List<ACEAider> aces = new ArrayList<>();
        final ACLPermission aclPermission = (ACLPermission) perm;
        for (int i = 0; i < aclPermission.getACECount(); i++) {
            aces.add(new ACEAider(aclPermission.getACEAccessType(i), aclPermission.getACETarget(i), aclPermission.getACEWho(i), aclPermission.getACEMode(i)));
        }
        return aces;
    }

    @Override
    public void setPermissions(final Resource res, final Permission perm) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            if (perm instanceof ACLPermission) {
                collection.getClient().setPermissions(path, perm.getOwner().getName(), perm.getGroup().getName(), perm.getMode(), getACEs(perm));
            } else {
                collection.getClient().setPermissions(path, perm.getOwner().getName(), perm.getGroup().getName(), perm.getMode());
            }
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void setPermissions(final Collection child, final Permission perm) throws XMLDBException {
        final String path = ((RemoteCollection) child).getPath();
        try {
            if (perm instanceof ACLPermission) {
                collection.getClient().setPermissions(path, perm.getOwner().getName(), perm.getGroup().getName(), perm.getMode(), getACEs(perm));
            } else {
                collection.getClient().setPermissions(path, perm.getOwner().getName(), perm.getGroup().getName(), perm.getMode());
            }
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void setPermissions(final Collection child, final String owner, final String group, final int mode, final List<ACEAider> aces) throws XMLDBException {
        final String path = ((RemoteCollection) child).getPath();
        try {
            if (aces != null) {
                collection.getClient().setPermissions(path, owner, group, mode, aces);
            } else {
                collection.getClient().setPermissions(path, owner, group, mode);
            }
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void setPermissions(final Resource res, final String owner, final String group, final int mode, final List<ACEAider> aces) throws XMLDBException {
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            if (aces != null) {
                collection.getClient().setPermissions(path, owner, group, mode, aces);
            } else {
                collection.getClient().setPermissions(path, owner, group, mode);
            }
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void chmod(final Resource res, final String mode) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            collection.getClient().setPermissions(path, mode);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void chmod(final Resource res, final int mode) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            collection.getClient().setPermissions(path, mode);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void chmod(final String mode) throws XMLDBException {
        try {
            collection.getClient().setPermissions(collection.getPath(), mode);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void chmod(final int mode) throws XMLDBException {
        try {
            collection.getClient().setPermissions(collection.getPath(), mode);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void lockResource(final Resource res, final Account u) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            collection.getClient().lockResource(path, u.getName());
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public String hasUserLock(final Resource res) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            final String userName = collection.getClient().hasUserLock(path);
            return userName != null && userName.length() > 0 ? userName : null;
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void unlockResource(final Resource res) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            collection.getClient().unlockResource(path);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void chgrp(final String group) throws XMLDBException {
        try {
            collection.getClient().chgrp(collection.getPath(), group);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void chown(final Account u) throws XMLDBException {
        try {
            collection.getClient().chown(collection.getPath(), u.getName());
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void chown(final Account u, final String group) throws XMLDBException {
        try {
            collection.getClient().chown(collection.getPath(), u.getName(), group);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void chgrp(final Resource res, final String group) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            collection.getClient().chgrp(path, group);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void chown(final Resource res, final Account u) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            collection.getClient().chown(path, u.getName());
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void chown(final Resource res, final Account u, final String group) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            collection.getClient().chown(path, u.getName(), group);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public Date getSubCollectionCreationTime(final Collection cParent, final String name) throws XMLDBException {
        if (collection == null) {
            throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "collection is null");
        }

        Long creationTime;
        try {
            creationTime = ((RemoteCollection) cParent).getSubCollectionCreationTime(name);

            if (creationTime == null) {
                creationTime = collection.getClient().getSubCollectionCreationTime(((RemoteCollection) cParent).getPath(), name);
            }
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }

        return new Date(creationTime);
    }

    @Override
    public Permission getSubCollectionPermissions(final Collection cParent, final String name) throws XMLDBException {
        if (collection == null) {
            throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "collection is null");
        }

        Permission perm;
        try {
            perm = ((RemoteCollection) cParent).getSubCollectionPermissions(name);

            if (perm == null) {

                final Map<String, Object> result = collection.getClient().getSubCollectionPermissions(((RemoteCollection) cParent).getPath(), name);

                final String owner = (String) result.get("owner");
                final String group = (String) result.get("group");
                final int mode = (Integer) result.get("permissions");
                final Stream<ACEAider> aces = extractAces(result.get("acl"));

                perm = getPermission(owner, group, mode, aces);
            }
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }

        return perm;
    }

    @Override
    public Permission getSubResourcePermissions(final Collection cParent, final String name) throws XMLDBException {
        if (collection == null) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "collection is null");
        }

        Permission perm;
        try {
            perm = ((RemoteCollection) cParent).getSubCollectionPermissions(name);

            if (perm == null) {
                final Map<String, Object> result = collection.getClient().getSubResourcePermissions(((RemoteCollection) cParent).getPath(), name);

                final String owner = (String) result.get("owner");
                final String group = (String) result.get("group");
                final int mode = (Integer) result.get("permissions");
                final Stream<ACEAider> aces = extractAces(result.get("acl"));

                perm = getPermission(owner, group, mode, aces);
            }
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }

        return perm;
    }

    @Override
    public Permission getPermissions(final Collection coll) throws XMLDBException {
        if (coll == null) {
            throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "collection is null");
        }

        try {
            final Map<String, Object> result = collection.getClient().getPermissions(((RemoteCollection) coll).getPath());

            final String owner = (String) result.get("owner");
            final String group = (String) result.get("group");
            final int mode = (Integer) result.get("permissions");
            final Stream<ACEAider> aces = extractAces(result.get("acl"));

            return getPermission(owner, group, mode, aces);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public Permission getPermissions(final Resource res) throws XMLDBException {
        if (res == null) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "resource is null");
        }

        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            final Map result = (Map) collection.getClient().getPermissions(path);

            final String owner = (String) result.get("owner");
            final String group = (String) result.get("group");
            final int mode = (Integer) result.get("permissions");
            final Stream<ACEAider> aces = extractAces(result.get("acl"));

            return getPermission(owner, group, mode, aces);

        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public Permission[] listResourcePermissions() throws XMLDBException {
        try {
            final Map<String, List> result = collection.getClient().listDocumentPermissions(collection.getPath());
            final Permission perm[] = new Permission[result.size()];
            final String[] resources = collection.listResources();
            for (int i = 0; i < resources.length; i++) {
                final List t = result.get(resources[i]);

                final String owner = (String) t.get(0);
                final String group = (String) t.get(1);
                final int mode = (Integer) t.get(2);
                final Stream<ACEAider> aces = extractAces(t.get(3));

                perm[i] = getPermission(owner, group, mode, aces);
            }
            return perm;
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public Permission[] listCollectionPermissions() throws XMLDBException {
        try {
            final Map<XmldbURI, List> result = collection.getClient().listCollectionPermissions(collection.getPath());
            final Permission perm[] = new Permission[result.size()];
            final String collections[] = collection.listChildCollections();
            for (int i = 0; i < collections.length; i++) {
                final List t = result.get(collections[i]);

                final String owner = (String) t.get(0);
                final String group = (String) t.get(1);
                final int mode = (Integer) t.get(2);
                final Stream<ACEAider> aces = extractAces(t.get(3));

                perm[i] = getPermission(owner, group, mode, aces);
            }
            return perm;
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public String getProperty(final String name) {
        return null;
    }

    @Override
    public Account getAccount(final String name) {
        try {
            final Map<String, Object> tab = collection.getClient().getAccount(name);

            if (tab == null || tab.isEmpty()) {
                return null;
            }

            final UserAider u;
            if (tab.get("default-group-id") != null) {
                final GroupAider defaultGroup = new GroupAider(
                        (Integer) tab.get("default-group-id"),
                        (String) tab.get("default-group-realmId"),
                        (String) tab.get("default-group-name")
                );

                u = new UserAider(
                        (String) tab.get("realmId"),
                        (String) tab.get("name"),
                        defaultGroup
                );
            } else {
                u = new UserAider(
                        (String) tab.get("realmId"),
                        (String) tab.get("name")
                );
            }

            final Object[] groups = (Object[]) tab.get("groups");
            for (final Object group : groups) {
                u.addGroup((String) group);
            }

            u.setEnabled(Boolean.valueOf((String) tab.get("enabled")));
            u.setUserMask((Integer) tab.get("umask"));

            final Map<String, String> metadata = (Map<String, String>) tab.get("metadata");
            for (final Map.Entry<String, String> m : metadata.entrySet()) {
                if (AXSchemaType.valueOfNamespace(m.getKey()) != null) {
                    u.setMetadataValue(AXSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                } else if (EXistSchemaType.valueOfNamespace(m.getKey()) != null) {
                    u.setMetadataValue(EXistSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                }
            }

            return u;
        } catch (final EXistException | PermissionDeniedException e) {
            return null;
        }
    }

    @Override
    public Account[] getAccounts() throws XMLDBException {
        try {
            final List<Map<String, Object>> users = collection.getClient().getAccounts();

            final UserAider[] u = new UserAider[users.size()];
            for (int i = 0; i < u.length; i++) {
                final Map<String, Object> tab = users.get(i);

                int uid = -1;
                try {
                    uid = (Integer) tab.get("uid");
                } catch (final java.lang.NumberFormatException e) {

                }

                u[i] = new UserAider(uid, (String) tab.get("realmId"), (String) tab.get("name"));
                final Object[] groups = (Object[]) tab.get("groups");
                for (int j = 0; j < groups.length; j++) {
                    u[i].addGroup((String) groups[j]);
                }

                u[i].setEnabled(Boolean.valueOf((String) tab.get("enabled")));
                u[i].setUserMask((Integer) tab.get("umask"));

                final Map<String, String> metadata = (Map<String, String>) tab.get("metadata");
                for (final Map.Entry<String, String> m : metadata.entrySet()) {
                    if (AXSchemaType.valueOfNamespace(m.getKey()) != null) {
                        u[i].setMetadataValue(AXSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                    } else if (EXistSchemaType.valueOfNamespace(m.getKey()) != null) {
                        u[i].setMetadataValue(EXistSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                    }
                }
            }
            return u;
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public Group getGroup(final String name) throws XMLDBException {
        try {
            final Map<String, Object> tab = collection.getClient().getGroup(name);

            if (tab != null && !tab.isEmpty()) {
                final Group group = new GroupAider((Integer) tab.get("id"), (String) tab.get("realmId"), (String) tab.get("name"));

                final Object[] managers = (Object[]) tab.get("managers");
                for (final Object manager : managers) {
                    group.addManager(getAccount((String) manager));
                }

                final Map<String, String> metadata = (Map<String, String>) tab.get("metadata");
                for (final Map.Entry<String, String> m : metadata.entrySet()) {
                    if (AXSchemaType.valueOfNamespace(m.getKey()) != null) {
                        group.setMetadataValue(AXSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                    } else if (EXistSchemaType.valueOfNamespace(m.getKey()) != null) {
                        group.setMetadataValue(EXistSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                    }
                }

                return group;
            }
            return null;
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void removeAccount(final Account u) throws XMLDBException {
        try {
            collection.getClient().removeAccount(u.getName());
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void removeGroup(final Group role) throws XMLDBException {
        try {
            collection.getClient().removeGroup(role.getName());
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void setCollection(final Collection collection) {
        this.collection = (RemoteCollection) collection;
    }

    @Override
    public void setProperty(final String property, final String value) {
    }

    @Override
    public void updateAccount(final Account user) throws XMLDBException {
        try {
            final Map<String, String> metadata = new HashMap<>();
            for (final SchemaType key : user.getMetadataKeys()) {
                metadata.put(key.getNamespace(), user.getMetadataValue(key));
            }

            collection.getClient().updateAccount(
                    user.getName(),
                    user.getPassword() == null ? "" : user.getPassword(),
                    user.getDigestPassword() == null ? "" : user.getDigestPassword(),
                    Arrays.asList(user.getGroups()),
                    user.isEnabled(),
                    user.getUserMask(),
                    metadata
            );
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void updateGroup(final Group group) throws XMLDBException {
        try {
            final List<String> managers = new ArrayList<>(group.getManagers().size());
            for (int i = 0; i < group.getManagers().size(); i++) {
                managers.add(group.getManagers().get(i).getName());
            }

            final Map<String, String> metadata = new HashMap<>();
            for (final SchemaType key : group.getMetadataKeys()) {
                metadata.put(key.getNamespace(), group.getMetadataValue(key));
            }

            collection.getClient().updateGroup(group.getName(), managers, metadata);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public String[] getGroupMembers(final String groupName) throws XMLDBException {
        try {
            final List<String> results = collection.getClient().getGroupMembers(groupName);
            return results.toArray(new String[results.size()]);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void addAccountToGroup(final String accountName, final String groupName) throws XMLDBException {
        try {
            collection.getClient().addAccountToGroup(accountName, groupName);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void addGroupManager(final String manager, final String groupName) throws XMLDBException {
        try {
            collection.getClient().addGroupManager(manager, groupName);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void removeGroupManager(final String groupName, final String manager) throws XMLDBException {
        try {
            collection.getClient().removeGroupManager(groupName, manager);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void addUserGroup(final Account user) throws XMLDBException {
        try {
            collection.getClient().updateAccount(user.getName(), Arrays.asList(user.getGroups()));
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void removeGroupMember(final String group, final String account) throws XMLDBException {
        try {
            collection.getClient().removeGroupMember(group, account);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public String[] getGroups() throws XMLDBException {
        try {
            final List<String> result = collection.getClient().getGroups();
            return result.toArray(new String[result.size()]);
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void addUser(final User user) throws XMLDBException {
        final Account account = new UserAider(user.getName());
        addAccount(account);
    }

    @Override
    public void updateUser(final User user) throws XMLDBException {
        final Account account = new UserAider(user.getName());
        account.setPassword(user.getPassword());
        //TODO: groups
        updateAccount(account);
    }

    @Override
    public User getUser(final String name) {
        return getAccount(name);
    }

    @Override
    public User[] getUsers() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void removeUser(final User user) {
		// TODO Auto-generated method stub

    }

    @Override
    public void lockResource(final Resource res, final User u) throws XMLDBException {
        final Account account = new UserAider(u.getName());
        lockResource(res, account);
    }
}
