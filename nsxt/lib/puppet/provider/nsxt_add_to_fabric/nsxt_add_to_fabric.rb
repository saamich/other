# require ruby-rest-client, ruby-json

require File.join(File.dirname(__FILE__),'..', 'nsxtapi')

Puppet::Type.type(:nsxt_add_to_fabric).provide(:nsxt_add_to_fabric, :parent => Puppet::Provider::Nsxtapi) do
  
  commands :nsxcli => 'nsxcli'

  def create
    retry_count = 3
    while retry_count > 0
      out = nsxtcli("join management-plane #{@resource[:manager]} username #{@resource[:username]} thumbprint #{@resource[:thumbprint]} password '#{@resource[:password]}'")
      if exists?
        notice("Node added to NSX-T fabric")
        return true
      else
        retry_count -= 1
        sleep 2
      end
    end
    raise Puppet::Error,("\n Node not add to NSX-t fabric, error: \n #{out}")
  end

  def exists?
    managers = nsxtcli("get managers")
    if managers.include? "Connected" and check_node_registered
      return true
    end
    return false
  end

  def destroy
    info("\n Does not support 'ensure=absent' \n It could be 'ensure=present' ONLY!!! ")
  end

  def get_node_uuid
    uuid = nsxtcli("get node-uuid")
    if uuid =~ /\A[\da-f]{32}\z/i or uuid =~ /\A(urn:uuid:)?[\da-f]{8}-([\da-f]{4}-){3}[\da-f]{12}\z/i
      return uuid
    end
    notice("Cannot get node uuid")
    return ""
  end

  def nsxtcli(cmd)
    out = nsxcli(['-c', cmd])
    return out.to_s.strip
  end

  def check_node_registered
    node_uuid = get_node_uuid
    if not node_uuid.empty?
      api_url = "https://#{manager}/api/v1/fabric/nodes/#{node_uuid}/state"
      response = get_nsxt_api(api_url, username, password)
      return true if response['state'] == 'success'
    end
    return false
  end

end
