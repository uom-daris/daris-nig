#============================================================================#
# Register generic meta-data with specific PSSD objects                      #
# This is domain-specific, but not method-specific meta-data                 #
#============================================================================#

# Notifications
set mtypeArgs ":mtype -requirement optional daris:pssd-notification"

# Governance (funding, ethocs etc)
set mtypeArgs "${mtypeArgs} :mtype -requirement optional daris:pssd-project-governance"

# Research categories
set mtypeArgs "${mtypeArgs} :mtype -requirement optional daris:pssd-project-research-category"

# Generic Project owner 
set mtypeArgs "${mtypeArgs} :mtype -requirement optional daris:pssd-project-owner"

# DICOM actions
set mtypeArgs "${mtypeArgs} :mtype -requirement optional daris:pssd-dicom-ingest"

# ANDS harvesting
#set mtypeArgs "${mtypeArgs} :mtype -requirement optional daris:pssd-project-harvest"

# Replace meta-data
set args ":append false :type project ${mtypeArgs}"
om.pssd.type.metadata.set $args

