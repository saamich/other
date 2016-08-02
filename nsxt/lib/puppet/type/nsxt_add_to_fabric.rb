Puppet::Type.newtype(:nsxt_add_to_fabric) do

  @doc = "Add kvm node to NSX-T fabric."

  ensurable do
    defaultto :present
    newvalue(:present) do
      provider.create
    end
  end

  newparam(:manager) do
    isnamevar
    desc 'The address of NSX-T manager.'
  end

  newparam(:username) do
    desc 'The user name for login to NSX-T manager.'
  end

  newparam(:password) do
    desc 'The password for login to NSX-T manager.'
  end

  newparam(:thumbprint) do
    desc 'Thumbprint of the NSX-T manager.'
  end

end
