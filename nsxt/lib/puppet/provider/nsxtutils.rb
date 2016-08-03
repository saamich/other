# require ruby-rest-client, ruby-json

require 'rest-client'
require 'json'
require 'socket'
require 'openssl'

class Puppet::Provider::Nsxtutils < Puppet::Provider

  def get_nsxt_api(api_url, username, password, timeout=5, ca_file)
    retry_count = 3
    begin
      if ca_file.to_s.empty?
        response = RestClient::Request.execute(method: :get, url: api_url, timeout: timeout, user: username, password: password, verify_ssl: false) 
      else
        response = RestClient::Request.execute(method: :get, url: api_url, timeout: timeout, user: username, password: password, ssl_ca_file: ca_file)
      end
      response_hash = JSON.parse(response.body)
      return response_hash
    rescue Errno::ECONNREFUSED
      notice("\n\nCan not get response from #{api_url} - 'Connection refused', try next if exist\n")
      return ""
    rescue Errno::EHOSTUNREACH
      notice("\n\nCan not get response from #{api_url} - 'No route to host', try next if exist\n")
      return ""
    rescue => error
      retry_count -= 1
      if retry_count > 0
        sleep 10
        retry
      else
        raise Puppet::Error,("\n\nCan not get response from #{api_url} :\n#{error.message}\n")
      end
    end
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
    out_cli = nsxcli(['-c', cmd]).to_s.strip
    debug("cmd out:\n #{out_cli}")
    return out_cli
  end

  def get_manager_ip_port(manager)
    # scheme does not take into account, NSX-T 1.0 supports only https
    manager =~ /(https:\/\/)?(?<ip>[^:]+):?(?<port>\d+)?/
    manager_ip_port = {}
    manager_ip_port['ip']= Regexp.last_match[:ip]
    port = Regexp.last_match[:port]
    port = 443 if port.to_s.empty?
    manager_ip_port['port'] = port
    return manager_ip_port
  end

  def get_manager_thumbprint(manager, timeout=5, ca_file)
    manager_ip_port = get_manager_ip_port(manager)
    ip = manager_ip_port['ip']
    port = manager_ip_port['port']
    retry_count = 3
    begin
      tcp_client = TCPSocket.new(ip, port, timeout)
      ssl_context = OpenSSL::SSL::SSLContext.new()
      if ca_file.to_s.empty?
        ssl_context.verify_mode = OpenSSL::SSL::VERIFY_NONE
      else
        ssl_context.verify_mode = OpenSSL::SSL::VERIFY_PEER
        ssl_context.ca_file = ca_file
      end
      ssl_client = OpenSSL::SSL::SSLSocket.new(tcp_client, ssl_context)
      ssl_client.connect
      cert = OpenSSL::X509::Certificate.new(ssl_client.peer_cert)
      ssl_client.sysclose
      tcp_client.close
      tp = OpenSSL::Digest::SHA256.new(cert.to_der)
      return OpenSSL::Digest::SHA256.new(cert.to_der).to_s
    rescue Errno::ECONNREFUSED
      notice("\n\nCan not get 'thumbprint' from #{ip}:#{port} - 'Connection refused', try next if exist\n")
      return ""
    rescue Errno::EHOSTUNREACH
      notice("\n\nCan not get 'thumbprint' from #{ip}:#{port} - 'No route to host', try next if exist\n")
      return ""
    rescue => error
      retry_count -= 1
      if retry_count > 0
        sleep 5
        retry
      else
        raise Puppet::Error,("\n\nCan not get thumbprint from #{ip}:#{port} :\n#{error.message}\n")
      end
    end
  end

end
