#!/usr/bin/env python
#
# Cloudlet Infrastructure for Mobile Computing
#
#   Author: Zhuo Chen <zhuoc@cs.cmu.edu>
#
#   Copyright (C) 2011-2013 Carnegie Mellon University
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

import fcntl
import json
import socket
import struct
import urllib2

import gabriel


def get_ip(iface = 'en0'):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sockfd = sock.fileno()
    SIOCGIFADDR = 0x8915

    ifreq = struct.pack('16sH14s', iface, socket.AF_INET, '\x00' * 14)
    try:
        res = fcntl.ioctl(sockfd, SIOCGIFADDR, ifreq)
    except:
        return "127.0.0.1"
    ip = struct.unpack('16sH2x4s8x', res)[2]

    return socket.inet_ntoa(ip)


def get_registry_server_address(address = None):
    # get ip and port for registry server
    if address is None:
        UPnP_client = gabriel.network.UPnPClient()
        UPnP_client.start()
        UPnP_client.join()
        ip_addr = UPnP_client.http_ip
        port = UPnP_client.http_port
    else:
        ip_addr, port = address.split(":", 1)
        port = int(port)
    return (ip_addr, port)


def get_service_list(ip_addr, port):
    url = "http://%s:%d/" % (ip_addr, port)
    service_list = gabriel.network.http_get(url)
    return service_list
