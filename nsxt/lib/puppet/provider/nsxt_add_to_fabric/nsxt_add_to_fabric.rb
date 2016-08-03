require File.join(File.dirname(__FILE__),'..', 'nsxtutils')

Puppet::Type.type(:nsxt_add_to_fabric).provide(:nsxt_add_to_fabric, :parent => Puppet::Provider::Nsxtutils) do
 
  # need this for work nsxtcli method, otherwise not work
  commands :nsxcli => 'nsxcli'

  def create
    debug("Attempting to register a node")
    retry_count = 3
    # need define for return error from cycle
    out_reg = ''
    @resource[:managers].each do |manager|
      thumbprint = get_manager_thumbprint(manager, ca_file = @resource[:ca_file])
      if not thumbprint.empty?
        while retry_count > 0
          out_reg = nsxtcli("join management-plane #{manager} username #{@resource[:username]} thumbprint #{thumbprint} password #{@resource[:password]}")
          # need sleep before check - for NSX-T manager have time update the status 
          sleep 15
          if exists?
            notice("Node added to NSX-T fabric")
            return true
          else
            retry_count -= 1
            sleep 2
          end
        end
      end
    end
    raise Puppet::Error,("\n\n Node not add to NSX-t fabric:\n #{out_reg}\n")
  end

  def exists?
    connected_managers = nsxtcli("get managers")
    if connected_managers.include? "Connected"
      node_uuid = get_node_uuid
      @resource[:managers].each do |manager|
        if check_node_registered(manager, node_uuid)
          debug("Node '#{node_uuid}' connected and registered on '#{manager}'")
          return true
        end
      end
    end
    debug("Node NOT registered on NSX-T manager")
    return false
  end

  def destroy
    debug("Attempting to unregister a node")
    retry_count = 3
    # need define for return error from cycle
    out_unreg = ''
    @resource[:managers].each do |manager|
      thumbprint = get_manager_thumbprint(manager, ca_file = @resource[:ca_file])
      if not thumbprint.empty?
        while retry_count > 0
          out_unreg = nsxtcli("detach management-plane #{manager} username #{@resource[:username]} thumbprint #{thumbprint} password #{@resource[:password]}")
          if not exists?
            notice("Node deleted from NSX-T fabric")
            return true
          else
            retry_count -= 1
            sleep 2
          end
        end
      end
    end
    raise Puppet::Error,("\n\n Node not deleted from NSX-t fabric: \n #{out_unreg}\n")
  end

  def check_node_registered(manager, node_uuid)
    manager_ip_port = get_manager_ip_port(manager)
    ip = manager_ip_port['ip']
    port = manager_ip_port['port']
    if not node_uuid.empty?
      api_url = "https://#{ip}:#{port}/api/v1/fabric/nodes/#{node_uuid}/state"
      response = get_nsxt_api(api_url, @resource[:username], @resource[:password], @resource[:ca_file])
      if response['state'] == 'success'
        debug("Node '#{node_uuid}' registered on '#{ip}:#{port}'")
        return true
      end
    end
    debug("Node NOT registered on '#{ip}:#{port}'")
    return false
  end

end
