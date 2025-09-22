sudo apt install curl gnupg

sudo mkdir -p /etc/apt/keyrings
curl https://packages.confluent.io/confluent-cli/deb/archive.key | sudo gpg --dearmor -o /etc/apt/keyrings/confluent-cli.gpg
sudo chmod go+r /etc/apt/keyrings/confluent-cli.gpg

echo "deb [signed-by=/etc/apt/keyrings/confluent-cli.gpg] https://packages.confluent.io/confluent-cli/deb stable main" | sudo tee /etc/apt/sources.list.d/confluent-cli.list >/dev/null

sudo apt update

sudo apt install confluent-cli