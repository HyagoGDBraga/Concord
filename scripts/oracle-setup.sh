#!/usr/bin/env bash
#
# Preparação de uma VM Oracle Cloud Always Free (Ubuntu 24.04 ARM) para o
# Concord.
#
# Rode UMA VEZ, como o usuário 'ubuntu', na máquina recém-criada:
#   curl -fsSL <url-deste-arquivo> -o oracle-setup.sh && bash oracle-setup.sh
#
# O que ele resolve, em ordem de quanto tempo costuma custar quando esquecido:
#   1. As regras de iptables que a imagem da Oracle traz e que bloqueiam tudo
#   2. Docker + Compose no ARM
#   3. Swap (o build do Maven consome mais do que parece)
#   4. Fuso horário e atualizações automáticas de segurança

set -euo pipefail

echo "==> Atualizando o sistema"
sudo apt-get update
sudo apt-get upgrade -y

echo "==> Instalando Docker"
# O script oficial detecta ARM sozinho.
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"

echo "==> Criando swap de 4 GB"
# A VM tem RAM de sobra, mas o build do Maven tem picos e o OOM killer no meio
# de um deploy é uma forma desagradável de descobrir isso.
if [ ! -f /swapfile ]; then
	sudo fallocate -l 4G /swapfile
	sudo chmod 600 /swapfile
	sudo mkswap /swapfile
	sudo swapon /swapfile
	echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

echo "==> Abrindo portas no firewall LOCAL da máquina"
# ESTE É O PASSO QUE MAIS GERA CONFUSÃO.
#
# A imagem Ubuntu da Oracle vem com regras de iptables que aceitam apenas SSH.
# Você pode liberar tudo no painel da OCI e o site continuar sem abrir, porque
# o bloqueio está DENTRO da máquina. São dois firewalls em série, e ambos
# precisam permitir.
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p udp --dport 443 -j ACCEPT
# TURN
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 3478 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p udp --dport 3478 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p udp --dport 49160:49200 -j ACCEPT

sudo netfilter-persistent save

echo "==> Ajustando fuso horário"
sudo timedatectl set-timezone America/Sao_Paulo

echo "==> Habilitando atualizações automáticas de segurança"
sudo apt-get install -y unattended-upgrades
sudo dpkg-reconfigure -f noninteractive unattended-upgrades

cat <<'FIM'

=====================================================================
Máquina preparada.

FALTA FAZER NO PAINEL DA ORACLE (não dá para automatizar daqui):

  Networking > Virtual Cloud Networks > sua VCN > Security Lists
  > Default Security List > Add Ingress Rules

  Source 0.0.0.0/0, TCP  destino 80
  Source 0.0.0.0/0, TCP  destino 443
  Source 0.0.0.0/0, UDP  destino 443
  Source 0.0.0.0/0, TCP  destino 3478
  Source 0.0.0.0/0, UDP  destino 3478
  Source 0.0.0.0/0, UDP  destino 49160-49200

Depois:
  1. Saia e entre de novo no SSH (para o grupo docker valer)
  2. Aponte o DNS do seu domínio para o IP público desta máquina
  3. Espere o DNS propagar ANTES de subir — senão o Let's Encrypt falha
  4. git clone do projeto, cp .env.example .env, preencha e:
       docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
=====================================================================
FIM
