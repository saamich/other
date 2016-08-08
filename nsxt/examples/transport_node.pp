nsxt_create_transport_node { 'Register node':
  managers => 'https://172.16.0.245, https://172.16.0.249:443',
  username => 'admin',
  password => 'Qwer!1234',
#  ensure => absent,
  ensure => present,
  host_switch_name => 'overlay-switch',
  host_switch_profile_ids => [{'key' => 'UplinkHostSwitchProfile', 'value' => '37e67225-d5fa-49d6-bcb7-0637b1064e4b'}],
  pnics => [{'device_name' => 'enp0s6', 'uplink_name' => 'uplink'}],
  static_ip_pool_id => '6861b43b-d9bf-43af-b379-47ba768c2920',
  transport_zone_id => 'd45a6956-e8cb-4f35-ae85-9a40a7b9bd87',
#  ca_file => '/tmp/ca.crt'
}
