#
# This service will grant certain services the rights to grant/revoke roles
# within the specified domain.  In this way, end-users can grant/revoke roles 
# for other users in a constrained way.  This is necessary because the MF 
# permissions model requires domain permissions for the underlying services.
# Any new domain you create you must do this if you want users to be able to
# grant/revoke roles.
#
# No longer necessary from MF 3.8.029 as the permissions model
# has been tightened and a new methodology is required 
# appropriate services get system-administrator role
om.pssd.domain.grant :domain nig
