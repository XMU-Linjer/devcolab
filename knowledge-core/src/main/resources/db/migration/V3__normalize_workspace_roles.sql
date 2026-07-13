UPDATE workspace_members
   SET role = 'ADMIN'
 WHERE role = 'OWNER';

UPDATE workspace_members
   SET role = 'MEMBER'
 WHERE role = 'VIEWER';
